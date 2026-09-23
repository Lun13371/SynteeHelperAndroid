package ru.syntee.helper.core.network.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import ru.syntee.helper.core.network.AppInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identity-заголовки Syntee на все запросы к gateway (статистика клиентов).
 * Gateway определяет тип клиента по подстроке "syntee" в User-Agent.
 */
@Singleton
class IdentityInterceptor @Inject constructor(
    private val appInfo: AppInfo,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "syntee-agent-android/${appInfo.versionName}")
            .header("X-Client-Name", "syntee-agent-android")
            .header("X-Client-Version", appInfo.versionName)
            .build()
        return chain.proceed(request)
    }
}
