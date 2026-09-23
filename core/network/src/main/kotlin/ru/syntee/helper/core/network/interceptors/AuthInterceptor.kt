package ru.syntee.helper.core.network.interceptors

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import ru.syntee.helper.core.datastore.TokenStore
import ru.syntee.helper.core.network.auth.TokenRefresher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bearer JWT + авто-refresh на 401 (один ретрай).
 *
 * Логика (контракт gateway):
 * ```
 * запрос + Bearer → 401?
 *   ├─ нет → пропускаем
 *   └─ да → TokenRefresher.refresh() (singleton, одноразовый токен)
 *          ├─ ок  → повторяем запрос с новым Bearer (1 раз)
 *          └─ null → возвращаем исходный 401 (UI увидит Auth-ошибку;
 *                    токены уже стёрты → AuthState → LoggedOut → LoginScreen)
 * ```
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val tokenRefresher: TokenRefresher,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenStore.getAccessToken() }
            ?: return chain.proceed(chain.request())

        val authedRequest = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        val response = chain.proceed(authedRequest)
        if (response.code != HTTP_UNAUTHORIZED) return response

        // 401: пробуем refresh (параллельные 401 ждут ОДИН refresh)
        val freshToken = runBlocking { tokenRefresher.refresh() }
            ?: return response // refresh не удался — отдаём исходный 401

        response.close()
        val retriedRequest = chain.request().newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
        return chain.proceed(retriedRequest)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
