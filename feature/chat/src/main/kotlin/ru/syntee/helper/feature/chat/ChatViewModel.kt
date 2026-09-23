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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.syntee.helper.core.data.chat.ChatRepository
import ru.syntee.helper.core.data.chat.ChatStreamManager
import ru.syntee.helper.core.data.model.Chat
import ru.syntee.helper.core.data.model.ChatMessage
import ru.syntee.helper.core.data.model.ChatModel
import ru.syntee.helper.core.data.model.MessageRole
import ru.syntee.helper.core.data.model.ModelsState
import ru.syntee.helper.core.data.model.StreamingState
import ru.syntee.helper.core.data.models.ModelsRepository
import ru.syntee.helper.core.datastore.SettingsPreferences
import javax.inject.Inject

/** Элемент списка сообщений на экране: финал из Room или живой стрим. */
sealed interface UiMessage {
    data class Final(val message: ChatMessage) : UiMessage
    data class Streaming(val state: StreamingState) : UiMessage
}

/**
 * Экран чата: история из Room + живой стрим из [ChatStreamManager]
 * (application-scope — переживает поворот экрана и навигацию).
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val streamManager: ChatStreamManager,
    private val modelsRepository: ModelsRepository,
    private val settings: SettingsPreferences,
) : ViewModel() {

    val chatId: String = savedStateHandle.get<String>("chatId").orEmpty()

    val modelsState: StateFlow<ModelsState> = modelsRepository.state

    /** Чат (заголовок, закреплённая модель). */
    val chat: StateFlow<Chat?> = chatRepository.observeChats()
        .map { list -> list.firstOrNull { it.id == chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** История сообщений (Room — источник истины). */
    val messages: StateFlow<List<ChatMessage>> = chatRepository.observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Живой стрим (null — сейчас не стримится). */
    val streaming: StateFlow<StreamingState?> = streamManager.stateFor(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** UI-список: история + стримящееся сообщение в конце. */
    val uiMessages: StateFlow<List<UiMessage>> = combine(messages, streaming) { msgs, stream ->
        val result: MutableList<UiMessage> = msgs.map { UiMessage.Final(it) }.toMutableList()
        if (stream != null && (!stream.isBlank || stream.error != null)) {
            result += UiMessage.Streaming(stream)
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Выбранная модель: закреплённая за чатом → дефолтная из настроек → null.
     */
    val selectedModel: StateFlow<ChatModel?> = combine(
        chat,
        modelsRepository.state,
        settings.settingsFlow,
    ) { chat, state, s ->
        val models = (state as? ModelsState.Loaded)?.models
            ?: (state as? ModelsState.Error)?.cached
            ?: emptyList()
        models.firstOrNull { it.id == chat?.modelId }
            ?: models.firstOrNull { it.id == s.defaultModelId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Недавние модели для пикера. */
    val recentModelIds: StateFlow<List<String>> = settings.settingsFlow
        .map { it.recentModelIds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val inputText = MutableStateFlow("")

    /** Событие ошибки для снекбара. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pickerVisible = MutableStateFlow(false)
    val pickerVisible: StateFlow<Boolean> = _pickerVisible.asStateFlow()

    init {
        viewModelScope.launch { modelsRepository.refresh() }
    }

    fun setInput(value: String) {
        inputText.value = value
    }

    fun consumeError() {
        _error.value = null
    }

    fun sendMessage() {
        val text = inputText.value.trim()
        if (text.isEmpty()) return
        val model = selectedModel.value
        if (model == null) {
            _error.value = "Сначала выберите модель"
            _pickerVisible.value = true
            return
        }
        inputText.value = ""
        viewModelScope.launch {
            chatRepository.insertUserMessage(chatId, text)
            val history = chatRepository.getRecentMessages(chatId, CONTEXT_MESSAGES)
            streamManager.start(chatId, model, history)
        }
    }

    /** Стоп-генерация: недописанный ответ сохранится с finishReason = cancelled. */
    fun stopGeneration() = streamManager.cancel(chatId)

    /** Повтор последнего запроса: убираем упавший ответ и стримим заново. */
    fun retryLast() {
        val model = selectedModel.value ?: return
        viewModelScope.launch {
            val last = messages.value.lastOrNull()
            if (last?.role == MessageRole.ASSISTANT &&
                last.finishReason in RETRYABLE
            ) {
                chatRepository.deleteMessage(last.id)
            }
            if (messages.value.isNotEmpty() || last?.role == MessageRole.USER) {
                val history = chatRepository.getRecentMessages(chatId, CONTEXT_MESSAGES)
                if (history.isNotEmpty()) streamManager.start(chatId, model, history)
            }
        }
    }

    fun renameChat(title: String) {
        viewModelScope.launch { chatRepository.renameChat(chatId, title) }
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
            chatRepository.setChatModel(chatId, model.id, model.ownedBy)
            settings.setDefaultModel(model.id)
            settings.pushRecentModel(model.id)
        }
        _pickerVisible.value = false
    }

    private companion object {
        const val CONTEXT_MESSAGES = 40
        val RETRYABLE = setOf("error", "cancelled", "interrupted")
    }
}
