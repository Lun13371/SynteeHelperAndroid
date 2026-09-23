package ru.syntee.helper.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Динамические базовые URL гейтвея.
 *
 * Приоритет:
 * 1. **Прод-режим** (переключатель в настройках, персистится) → https://syntee.fun
 * 2. Ручной override (dev-поля в настройках: LAN IP ПК для реального устройства)
 * 3. Дефолт сборки: debug → 10.0.2.2 (эмулятор), release → syntee.fun
 *
 * [BaseUrlInterceptor] подменяет host/scheme/port на лету.
 */
@Singleton
class UrlProvider @Inject constructor(
    private val appInfo: AppInfo,
) {

    @Volatile
    private var productionMode: Boolean = false

    @Volatile
    private var apiOverride: String? = null

    @Volatile
    private var webOverride: String? = null

    fun applySettings(production: Boolean, api: String?, web: String?) {
        productionMode = production
        apiOverride = api?.takeIf { it.isNotBlank() }
        webOverride = web?.takeIf { it.isNotBlank() }
    }

    val effectiveApiUrl: String
        get() = normalize(
            when {
                productionMode -> PROD_URL
                apiOverride != null -> apiOverride!!
                else -> appInfo.defaultApiUrl
            },
        )

    val effectiveWebUrl: String
        get() = normalize(
            when {
                productionMode -> PROD_URL
                webOverride != null -> webOverride!!
                else -> appInfo.defaultWebUrl
            },
        )

    val isProduction: Boolean
        get() = productionMode

    /** URL consent-страницы desktop-логина (валидация redirect по префиксу synteehelper://). */
    val loginUrl: String
        get() = "${effectiveWebUrl}/auth/desktop?redirect=synteehelper%3A%2F%2Fauth%2Fcallback"

    fun parseApiUrl(): okhttp3.HttpUrl? = effectiveApiUrl.toHttpUrlOrNull()

    private fun normalize(url: String): String = url.trim().trimEnd('/')

    private companion object {
        const val PROD_URL = "https://syntee.fun"
    }
}
