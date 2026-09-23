package ru.syntee.helper.core.network

/**
 * Информация о приложении для сетевого слоя (identity-заголовки, URL).
 * Провайдится в app-модуле из BuildConfig — library-модули не имеют BuildConfig.
 */
data class AppInfo(
    val versionName: String,
    val isDebug: Boolean,
    val defaultApiUrl: String,
    val defaultWebUrl: String,
)
