package ru.syntee.helper.core.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import ru.syntee.helper.core.data.model.GatewayUser
import ru.syntee.helper.core.datastore.AppSettings
import ru.syntee.helper.core.datastore.SettingsPreferences
import ru.syntee.helper.core.datastore.TokenPair
import ru.syntee.helper.core.datastore.TokenStore
import ru.syntee.helper.core.network.api.GatewayApi
import ru.syntee.helper.core.network.dto.ExchangeRequest
import ru.syntee.helper.core.network.dto.GatewayUserDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Состояние авторизации приложения. */
sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data object Guest : AuthState
    data class LoggedIn(val user: GatewayUser?) : AuthState
}

/**
 * Авторизация Syntee-Gateway: deep link флоу + JWT + гостевой режим.
 *
 * Флоу: Custom Tabs → consent ({WEB}/auth/desktop) → deep link с кодом →
 * [exchangeCode] → JWT в TokenStore (Keystore) → [AuthState.LoggedIn].
 */
@Singleton
class AuthRepository @Inject constructor(
    private val gatewayApi: GatewayApi,
    private val tokenStore: TokenStore,
    private val settings: SettingsPreferences,
    private val json: Json,
) {

    val authState: Flow<AuthState> =
        combine(tokenStore.tokensFlow, settings.settingsFlow) { tokens: TokenPair?, s: AppSettings ->
            when {
                s.isGuest -> AuthState.Guest
                tokens != null -> AuthState.LoggedIn(s.userJson?.let(::parseUser))
                else -> AuthState.LoggedOut
            }
        }

    /**
     * Обмен одноразового кода (TTL 10с!) на JWT-пару.
     * Токены сохраняются немедленно, затем подтягивается профиль.
     */
    suspend fun exchangeCode(code: String): Result<GatewayUser> = runCatching {
        val response = gatewayApi.exchangeCode(ExchangeRequest(code))
        tokenStore.setTokens(response.access_token, response.refresh_token)
        settings.setGuestMode(false)
        loadAndCacheUser().getOrThrow()
    }

    /** Профиль /api/user/auth/me (обновляет кэш для мгновенного старта). */
    suspend fun loadAndCacheUser(): Result<GatewayUser> = runCatching {
        val dto = gatewayApi.getMe()
        val user = dto.toDomain()
        settings.setUserJson(json.encodeToString(user))
        user
    }

    suspend fun setGuestMode(enabled: Boolean) {
        settings.setGuestMode(enabled)
    }

    /** Полный выход: токены + кэш профиля + гостевой флаг. */
    suspend fun logout() {
        tokenStore.clear()
        settings.setUserJson(null)
        settings.setGuestMode(false)
    }

    private fun parseUser(raw: String): GatewayUser? = runCatching {
        json.decodeFromString<GatewayUser>(raw)
    }.getOrNull()

    private fun GatewayUserDto.toDomain() = GatewayUser(
        id = id,
        email = email,
        username = username,
        displayName = display_name,
        avatarUrl = avatar_url,
        role = role,
    )
}
