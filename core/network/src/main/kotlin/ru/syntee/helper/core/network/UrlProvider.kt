package ru.syntee.helper.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Динамические базовые URL гейтвея.
 *
 * Дефолты приходят из BuildConfig (debug: 10.0.2.2, release: syntee.fun);
 * override-ы настраиваются в настройках (dev-режим, LAN IP для реального
 * устройства). [BaseUrlInterceptor] подменяет host/scheme/port на лету.
 */
@Singleton
class UrlProvider @Inject constructor(
    private val appInfo: AppInfo,
) {

    @Volatile
    private var apiOverride: String? = null

    @Volatile
    private var webOverride: String? = null

    fun applyOverrides(api: String?, web: String?) {
        apiOverride = api?.takeIf { it.isNotBlank() }
        webOverride = web?.takeIf { it.isNotBlank() }
    }

    val effectiveApiUrl: String
        get() = normalize(apiOverride ?: appInfo.defaultApiUrl)

    val effectiveWebUrl: String
        get() = normalize(webOverride ?: appInfo.defaultWebUrl)

    /** URL consent-страницы desktop-логина (валидация redirect по префиксу synteehelper://). */
    val loginUrl: String
        get() = "${effectiveWebUrl}/auth/desktop?redirect=synteehelper%3A%2F%2Fauth%2Fcallback"

    fun parseApiUrl(): okhttp3.HttpUrl? = effectiveApiUrl.toHttpUrlOrNull()

    private fun normalize(url: String): String = url.trim().trimEnd('/')
}
