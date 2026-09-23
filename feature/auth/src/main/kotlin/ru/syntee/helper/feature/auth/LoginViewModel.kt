package ru.syntee.helper.feature.auth

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.syntee.helper.core.data.auth.AuthRepository
import ru.syntee.helper.core.data.auth.DeepLinkBus
import ru.syntee.helper.core.network.UrlProvider
import javax.inject.Inject

sealed interface LoginUiState {
    /** Начальный экран с кнопками. */
    data object Idle : LoginUiState

    /** Открыли Custom Tabs, ждём возврата по deep link (таймаут 120с). */
    data object WaitingForBrowser : LoginUiState

    /** Обмениваем код на JWT. */
    data object Exchanging : LoginUiState

    /** Успех — UI переходит дальше. */
    data object Success : LoginUiState

    data class Error(val message: String) : LoginUiState
}

/**
 * Логин через Syntee-Gateway: Custom Tabs → consent → deep link → exchange.
 *
 * Deep link код приходит через [DeepLinkBus] (MainActivity → bus → сюда).
 * Код одноразовый, TTL 10 секунд — обрабатываем немедленно.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deepLinkBus: DeepLinkBus,
    private val urlProvider: UrlProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var timeoutJob: Job? = null

    init {
        // Код из deep link (приложение могло быть запущено колд-стартом).
        viewModelScope.launch {
            deepLinkBus.code.collect { code ->
                if (code != null) exchange(code)
            }
        }
    }

    /** Кнопка «Войти»: открыть consent-страницу в Custom Tabs. */
    fun startLogin() {
        _uiState.value = LoginUiState.WaitingForBrowser
        runCatching {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            // ViewModel держит @ApplicationContext: startActivity из
            // application-context требует NEW_TASK (браузер открывается
            // отдельной задачей поверх приложения).
            intent.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.launchUrl(context, urlProvider.loginUrl.toUri())
        }.onFailure { t ->
            _uiState.value = LoginUiState.Error(
                "Не удалось открыть браузер: ${t.message ?: "нет браузера"}",
            )
        }
        // Таймаут ожидания возврата из браузера (как на десктопе, 120с).
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(BROWSER_TIMEOUT_MS)
            if (_uiState.value is LoginUiState.WaitingForBrowser) {
                _uiState.value = LoginUiState.Error(
                    "Не дождались ответа от браузера. Проверьте, что вошли на сайте и разрешили доступ.",
                )
            }
        }
    }

    /** Отмена ожидания (кнопка «Отмена»). */
    fun cancelWaiting() {
        timeoutJob?.cancel()
        deepLinkBus.consume()
        _uiState.value = LoginUiState.Idle
    }

    /** Кнопка «Продолжить как гость». */
    fun continueAsGuest() {
        viewModelScope.launch {
            authRepository.setGuestMode(true)
            _uiState.value = LoginUiState.Success
        }
    }

    /** Повторить вход после ошибки. */
    fun reset() {
        deepLinkBus.consume()
        _uiState.value = LoginUiState.Idle
    }

    private fun exchange(code: String) {
        if (_uiState.value is LoginUiState.Exchanging) return
        timeoutJob?.cancel()
        _uiState.value = LoginUiState.Exchanging
        viewModelScope.launch {
            authRepository.exchangeCode(code)
                .onSuccess {
                    deepLinkBus.consume()
                    _uiState.value = LoginUiState.Success
                }
                .onFailure { t ->
                    deepLinkBus.consume()
                    _uiState.value = LoginUiState.Error(
                        t.message ?: "Код недействителен или истёк. Повторите вход.",
                    )
                }
        }
    }

    private companion object {
        const val BROWSER_TIMEOUT_MS = 120_000L
    }
}
