package ru.syntee.helper.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.syntee.helper.core.data.auth.AuthRepository
import ru.syntee.helper.core.data.auth.AuthState
import ru.syntee.helper.core.data.billing.Balance
import ru.syntee.helper.core.data.billing.BillingRepository
import ru.syntee.helper.core.data.chat.ChatRepository
import ru.syntee.helper.core.data.model.ChatModel
import ru.syntee.helper.core.data.model.ModelsState
import ru.syntee.helper.core.data.models.ModelsRepository
import ru.syntee.helper.core.datastore.AppSettings
import ru.syntee.helper.core.datastore.SettingsPreferences
import ru.syntee.helper.core.network.AppInfo
import ru.syntee.helper.core.network.UrlProvider
import javax.inject.Inject

/** Внутренняя навигация настроек (без отдельных маршрутов). */
enum class SettingsSection { ROOT, ACCOUNT, AI, DATA, ABOUT }

/** Состояние биллинга для карточки в «Аккаунте». */
sealed interface BillingUi {
    data object Loading : BillingUi
    data class Loaded(val balance: Balance) : BillingUi
    data class Error(val message: String) : BillingUi
}

/** Локальная статистика для секции «Данные». */
data class DataStatsUi(
    val chats: Int = 0,
    val messages: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val modelsRepository: ModelsRepository,
    private val billingRepository: BillingRepository,
    private val chatRepository: ChatRepository,
    private val settings: SettingsPreferences,
    private val urlProvider: UrlProvider,
    @ApplicationContext private val appContext: Context,
    val appInfo: AppInfo,
) : ViewModel() {

    private val _section = MutableStateFlow(SettingsSection.ROOT)
    val section: StateFlow<SettingsSection> = _section.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    val appSettings: StateFlow<AppSettings> = settings.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val modelsState: StateFlow<ModelsState> = modelsRepository.state

    /** Модель по умолчанию (для отображения в секции ИИ). */
    val defaultModel: StateFlow<ChatModel?> =
        combine(modelsRepository.state, settings.settingsFlow) { state, s ->
            val models = (state as? ModelsState.Loaded)?.models
                ?: (state as? ModelsState.Error)?.cached
                ?: emptyList()
            models.firstOrNull { it.id == s.defaultModelId }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _billing = MutableStateFlow<BillingUi>(BillingUi.Loading)
    val billing: StateFlow<BillingUi> = _billing.asStateFlow()

    private val _stats = MutableStateFlow(DataStatsUi())
    val stats: StateFlow<DataStatsUi> = _stats.asStateFlow()

    /** Событие «история удалена» → снекбар. */
    private val _historyCleared = MutableStateFlow(false)
    val historyCleared: StateFlow<Boolean> = _historyCleared.asStateFlow()

    /** Событие экспорта: null/ошибка/имя файла. */
    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    init {
        viewModelScope.launch { modelsRepository.refresh() }
        refreshStats()
    }

    fun open(section: SettingsSection) {
        _section.value = section
        when (section) {
            SettingsSection.ACCOUNT -> refreshBilling()
            SettingsSection.DATA -> refreshStats()
            else -> Unit
        }
    }

    fun back() {
        _section.value = SettingsSection.ROOT
    }

    // ── Аккаунт / биллинг ───────────────────────────────────────────────────

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun enterGuest() {
        viewModelScope.launch { authRepository.setGuestMode(true) }
    }

    fun refreshBilling() {
        viewModelScope.launch {
            _billing.value = BillingUi.Loading
            _billing.value = billingRepository.getBalance()
                .fold(
                    onSuccess = { BillingUi.Loaded(it) },
                    onFailure = { BillingUi.Error(it.message ?: "Не удалось загрузить баланс") },
                )
        }
    }

    /** Топап — Custom Tabs на {WEB}/billing (как на десктопе). */
    fun openTopUp() {
        val url = (urlProvider.effectiveWebUrl.removeSuffix("/") + "/billing").toUri()
        val intent = CustomTabsIntent.Builder().build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(appContext, url)
    }

    // ── ИИ и модели ─────────────────────────────────────────────────────────

    fun setDefaultModel(model: ChatModel) {
        viewModelScope.launch {
            settings.setDefaultModel(model.id)
            settings.pushRecentModel(model.id)
        }
    }

    fun setTemperature(value: Double) {
        viewModelScope.launch { settings.setTemperature(value) }
    }

    fun setMaxTokens(value: Int) {
        viewModelScope.launch { settings.setMaxTokens(value.coerceIn(256, 131_072)) }
    }

    fun setReasoningEffort(value: String) {
        viewModelScope.launch { settings.setReasoningEffort(value) }
    }

    // ── Данные ──────────────────────────────────────────────────────────────

    fun refreshStats() {
        viewModelScope.launch {
            chatRepository.getStats().let { s ->
                _stats.value = DataStatsUi(
                    chats = s.chats,
                    messages = s.messages,
                    promptTokens = s.promptTokens,
                    completionTokens = s.completionTokens,
                )
            }
        }
    }

    fun clearAllChats() {
        viewModelScope.launch {
            chatRepository.deleteAllChats()
            refreshStats()
            _historyCleared.value = true
        }
    }

    fun consumeHistoryCleared() {
        _historyCleared.value = false
    }

    /** Экспорт всех чатов в JSON по SAF-Uri (файл уже выбран диалогом). */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _exportResult.value = try {
                val json = chatRepository.exportAllJson()
                appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(json)
                } ?: throw IllegalStateException("Не удалось открыть файл")
                "История экспортирована"
            } catch (t: Throwable) {
                t.message ?: "Ошибка экспорта"
            }
        }
    }

    fun consumeExportResult() {
        _exportResult.value = null
    }

    // ── О приложении ────────────────────────────────────────────────────────

    fun applyUrlOverrides(api: String?, web: String?) {
        viewModelScope.launch { settings.setUrlOverrides(api, web) }
    }

    /** Переключатель прод-режима: все запросы → https://syntee.fun. */
    fun setProductionMode(enabled: Boolean) {
        viewModelScope.launch { settings.setProductionMode(enabled) }
    }

    fun effectiveApiUrl(): String = urlProvider.effectiveApiUrl
    fun effectiveWebUrl(): String = urlProvider.effectiveWebUrl
}
