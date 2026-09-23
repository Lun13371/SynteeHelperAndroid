package ru.syntee.helper.feature.chat

import androidx.lifecycle.SavedStateHandle
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
import ru.syntee.helper.core.data.model.ChatModel
import ru.syntee.helper.core.data.model.ModelsState
import ru.syntee.helper.core.data.models.ModelsRepository
import ru.syntee.helper.core.datastore.SettingsPreferences
import javax.inject.Inject

/**
 * Скелет стадии 02: модель чата + каталог моделей.
 * Стадия 03: стриминг SSE, история Room, автоскролл, отмена.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val modelsRepository: ModelsRepository,
    private val settings: SettingsPreferences,
) : ViewModel() {

    /** Идентификатор чата из маршрута ChatRoute (type-safe nav). */
    val chatId: String = savedStateHandle.get<String>("chatId").orEmpty()

    val modelsState: StateFlow<ModelsState> = modelsRepository.state

    /** Выбранная модель (defaultModelId из настроек, пока нет per-chat в Room). */
    val selectedModel: StateFlow<ChatModel?> =
        combine(modelsRepository.state, settings.settingsFlow) { state, s ->
            val models = (state as? ModelsState.Loaded)?.models
                ?: (state as? ModelsState.Error)?.cached
                ?: emptyList()
            models.firstOrNull { it.id == s.defaultModelId }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pickerVisible = MutableStateFlow(false)
    val pickerVisible: StateFlow<Boolean> = _pickerVisible.asStateFlow()

    init {
        viewModelScope.launch { modelsRepository.refresh() }
    }

    fun openPicker() {
        viewModelScope.launch { modelsRepository.refresh() }
        _pickerVisible.value = true
    }

    fun closePicker() {
        _pickerVisible.value = false
    }

    fun selectModel(model: ChatModel) {
        viewModelScope.launch {
            settings.setDefaultModel(model.id)
            settings.pushRecentModel(model.id)
        }
        _pickerVisible.value = false
    }
}
