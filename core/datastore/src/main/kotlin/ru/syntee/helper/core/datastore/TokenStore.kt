package ru.syntee.helper.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "syntee_auth")

data class TokenPair(val accessToken: String, val refreshToken: String)

/**
 * Хранение JWT-токенов: DataStore + шифрование Android Keystore (AES-GCM).
 *
 * - [tokensFlow] — реактивное состояние (для AuthState UI);
 * - [getAccessToken] — быстрое чтение для OkHttp-инцептора (in-memory кэш,
 *   runBlocking только при холодном старте процесса);
 * - refresh-токен одноразовый: [setTokens] вызывается НЕМЕДЛЕННО после обмена.
 */
interface TokenStore {
    val tokensFlow: Flow<TokenPair?>
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun setTokens(access: String, refresh: String)
    suspend fun clear()
}

@Singleton
class TokenStoreImpl @Inject constructor(
    private val context: Context,
    private val crypto: KeystoreCrypto,
) : TokenStore {

    private val accessKey = stringPreferencesKey("enc_access")
    private val refreshKey = stringPreferencesKey("enc_refresh")

    /** In-memory кэш для интерцептора (обновляется при каждой записи). */
    @Volatile
    private var cached: TokenPair? = null

    private val loadMutex = Mutex()

    override val tokensFlow: Flow<TokenPair?> = context.authDataStore.data.map { prefs ->
        prefs[accessKey]?.let { encA ->
            prefs[refreshKey]?.let { encR ->
                runCatching {
                    TokenPair(
                        accessToken = crypto.decrypt(decode(encA)).decodeToString(),
                        refreshToken = crypto.decrypt(decode(encR)).decodeToString(),
                    )
                }.getOrNull()
            }
        }
    }

    override suspend fun getAccessToken(): String? = ensureLoaded()?.accessToken

    override suspend fun getRefreshToken(): String? = ensureLoaded()?.refreshToken

    override suspend fun setTokens(access: String, refresh: String) {
        val pair = TokenPair(access, refresh)
        cached = pair // сначала кэш — токен уже «живой» для новых запросов
        context.authDataStore.edit { prefs ->
            prefs[accessKey] = encode(crypto.encrypt(access.toByteArray()))
            prefs[refreshKey] = encode(crypto.encrypt(refresh.toByteArray()))
        }
    }

    override suspend fun clear() {
        cached = null
        context.authDataStore.edit { prefs ->
            prefs.remove(accessKey)
            prefs.remove(refreshKey)
        }
    }

    private suspend fun ensureLoaded(): TokenPair? {
        cached?.let { return it }
        return loadMutex.withLock {
            cached?.let { return it }
            tokensFlow.first().also { cached = it }
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}
