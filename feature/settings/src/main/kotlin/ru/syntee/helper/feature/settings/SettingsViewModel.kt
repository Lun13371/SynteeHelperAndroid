package ru.syntee.helper.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.syntee.helper.core.data.auth.AuthRepository
import ru.syntee.helper.core.data.auth.AuthState
import ru.syntee.helper.core.data.model.ChatModel
import ru.syntee.helper.core.data.model.ModelsState
import ru.syntee.helper.core.data.models.ModelsRepository
import ru.syntee.helper.core.datastore.AppSettings
import ru.syntee.helper.core.datastore.SettingsPreferences
import ru.syntee.helper.core.network.AppInfo
import ru.syntee.helper.core.network.UrlProvider
import javax.inject.Inject

/** Внутренняя навигация настроек (без отдельных маршрутов). */
enum class SettingsSection { ROOT, ACCOUNT, AI, ABOUT }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val modelsRepository: ModelsRepository,
    private val settings: SettingsPreferences,
    private val urlProvider: UrlProvider,
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

    init {
        viewModelScope.launch { modelsRepository.refresh() }
    }

    fun open(section: SettingsSection) {
        _section.value = section
    }

    fun back() {
        _section.value = SettingsSection.ROOT
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun enterGuest() {
        viewModelScope.launch { authRepository.setGuestMode(true) }
    }

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
        viewModelScope.launch { settings.setMaxTokens(value) }
    }

    fun applyUrlOverrides(api: String?, web: String?) {
        viewModelScope.launch { settings.setUrlOverrides(api, web) }
    }

    fun effectiveApiUrl(): String = urlProvider.effectiveApiUrl
    fun effectiveWebUrl(): String = urlProvider.effectiveWebUrl
}
