package ru.syntee.helper.core.network.interceptors

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import ru.syntee.helper.core.network.UrlProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подменяет scheme/host/port запроса на актуальный базовый URL гейтвея
 * (dev 10.0.2.2 / прод syntee.fun / override из настроек).
 * Путь и query остаются от Retrofit-вызова.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val urlProvider: UrlProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base: HttpUrl = urlProvider.parseApiUrl() ?: return chain.proceed(request)
        val rewritten = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
