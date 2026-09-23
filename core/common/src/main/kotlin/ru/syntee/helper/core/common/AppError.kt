package ru.syntee.helper.core.common

/**
 * Единая модель ошибок приложения.
 * Маппинг в UI-строки происходит на уровне экранов (появляется в стадии 02+).
 */
sealed interface AppError {

    /** Нет сети / соединение оборвалось. */
    data class Network(val cause: Throwable? = null) : AppError

    /** Ошибка авторизации: токен невалиден, сессия истекла. */
    data class Auth(val message: String? = null) : AppError

    /** 429: слишком много запросов. */
    data class RateLimit(val retryAfterSeconds: Long? = null) : AppError

    /** Ошибка сервера (5xx) с деталью от gateway. */
    data class Server(val message: String? = null) : AppError

    /** Всё остальное. */
    data class Unknown(val cause: Throwable? = null) : AppError
}
