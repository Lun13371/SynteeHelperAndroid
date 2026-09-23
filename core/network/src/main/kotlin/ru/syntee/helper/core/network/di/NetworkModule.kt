package ru.syntee.helper.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.syntee.helper.core.network.AppInfo
import ru.syntee.helper.core.network.api.AuthApi
import ru.syntee.helper.core.network.api.GatewayApi
import ru.syntee.helper.core.network.interceptors.AuthInterceptor
import ru.syntee.helper.core.network.interceptors.BaseUrlInterceptor
import ru.syntee.helper.core.network.interceptors.IdentityInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GatewayClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Общий OkHttp для API-запросов: Bearer + refresh-on-401. */
    @Provides
    @Singleton
    @GatewayClient
    fun provideGatewayOkHttp(
        baseUrlInterceptor: BaseUrlInterceptor,
        identityInterceptor: IdentityInterceptor,
        authInterceptor: AuthInterceptor,
        appInfo: AppInfo,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(identityInterceptor)
        .addInterceptor(authInterceptor)
        .apply {
            if (appInfo.isDebug) {
                // ⚠️ Логируем только тело/статусы; заголовки НЕ логируем
                // (Bearer и refresh в body не должны попадать в логи).
                addInterceptor(
                    HttpLoggingInterceptor { msg ->
                        // маскируем Authorization, если вдруг появится
                        println(msg.replace(Regex("Bearer [^\\s]+"), "Bearer ***"))
                    }.apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** «Голый» OkHttp для refresh (без AuthInterceptor — иначе цикл и вечный 401). */
    @Provides
    @Singleton
    @AuthClient
    fun provideAuthOkHttp(
        baseUrlInterceptor: BaseUrlInterceptor,
        identityInterceptor: IdentityInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(identityInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @GatewayClient client: OkHttpClient,
        json: Json,
        appInfo: AppInfo,
    ): Retrofit = Retrofit.Builder()
        // Реальный URL подменяет BaseUrlInterceptor; это лишь заглушка для Retrofit.
        .baseUrl(appInfo.defaultApiUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGatewayApi(retrofit: Retrofit): GatewayApi = retrofit.create(GatewayApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(
        @AuthClient client: OkHttpClient,
        json: Json,
        appInfo: AppInfo,
    ): AuthApi = Retrofit.Builder()
        .baseUrl(appInfo.defaultApiUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AuthApi::class.java)
}
