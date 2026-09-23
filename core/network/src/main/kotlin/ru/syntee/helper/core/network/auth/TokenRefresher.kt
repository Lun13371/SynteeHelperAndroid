package ru.syntee.helper.core.network.auth

import ru.syntee.helper.core.datastore.TokenStore
import ru.syntee.helper.core.network.api.AuthApi
import ru.syntee.helper.core.network.dto.RefreshRequest
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Одноразовый refresh с singleton-promise.
 *
 * ⚠️ Контракт gateway: refresh-токен ОДНОРАЗОВЫЙ (атомарный blacklist,
 * Redis SET NX). Два параллельных refresh одним токеном → второй получит
 * 401 "already used" → ложный логаут. Поэтому все параллельные вызовы
 * [refresh] сливаются в ОДИН сетевой запрос (pending-паттерн).
 *
 * Новая пара сохраняется в TokenStore НЕМЕДЛЕННО — до любых других запросов
 * (крэш после consume, но до save = логаут).
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) {

    private val pending = AtomicReference<DeferredRefresh?>(null)

    /**
     * @return свежий access-токен или null (refresh невозможен — logout).
     */
    suspend fun refresh(): String? {
        val refresh = tokenStore.getRefreshToken() ?: return null

        // Уже идёт refresh — ждём его результат (singleton-promise).
        pending.get()?.let { return it.await() }

        val deferred = DeferredRefresh()
        if (!pending.compareAndSet(null, deferred)) {
            // Гонка: другой поток успел первым — ждём его.
            return pending.get()?.await()
        }

        val result = try {
            val response = authApi.refreshToken(RefreshRequest(refresh))
            tokenStore.setTokens(response.access_token, response.refresh_token)
            response.access_token
        } catch (t: Throwable) {
            // Невалидный / истёкший / already used → сессия мертва.
            tokenStore.clear()
            null
        } finally {
            pending.set(null)
        }
        deferred.complete(result)
        return result
    }

    /** Минимальный deferred без корутин-скоупа:CompletableFuture проще. */
    private class DeferredRefresh {
        private val future = java.util.concurrent.CompletableFuture<String?>()

        fun complete(value: String?) {
            future.complete(value)
        }

        fun await(): String? = try {
            future.get(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        } catch (t: Throwable) {
            null
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
    }
}
