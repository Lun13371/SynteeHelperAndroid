package ru.syntee.helper.core.network.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import ru.syntee.helper.core.network.dto.ExchangeRequest
import ru.syntee.helper.core.network.dto.GatewayUserDto
import ru.syntee.helper.core.network.dto.ModelsResponseDto
import ru.syntee.helper.core.network.dto.RefreshRequest
import ru.syntee.helper.core.network.dto.StatusBucketsDto
import ru.syntee.helper.core.network.dto.TokenResponse

/**
 * API Syntee-Gateway (пользовательская часть).
 *
 * Базовый URL подменяется BaseUrlInterceptor'ом (dev/prod/override).
 * Auth-запросы (exchange/refresh) идут через отдельный OkHttp без Bearer.
 */
interface GatewayApi {

    // ── Auth (без Bearer) ──

    /** Обмен одноразового кода (TTL 10 секунд!) на JWT-пару. */
    @POST("api/user/auth/desktop/exchange")
    suspend fun exchangeCode(@Body body: ExchangeRequest): TokenResponse

    /** Профиль текущего пользователя (Bearer). */
    @GET("api/user/auth/me")
    suspend fun getMe(): GatewayUserDto

    // ── Модели (Bearer) ──

    @GET("v1/models")
    suspend fun getModels(): ModelsResponseDto

    @GET("v1/model-status/buckets")
    suspend fun getStatusBuckets(@Query("hours") hours: Int = 12): List<StatusBucketsDto>
}

/**
 * Отдельный API для refresh-токенов: вызывается через «голый» OkHttp
 * (без AuthInterceptor — иначе цикл зависимостей и вечный 401).
 */
interface AuthApi {

    /**
     * Refresh-пара. ⚠️ Токен ОДНОРАЗОВЫЙ: новая пара сохраняется
     * немедленно до любых других действий.
     */
    @POST("api/user/auth/refresh")
    suspend fun refreshToken(@Body body: RefreshRequest): TokenResponse
}
