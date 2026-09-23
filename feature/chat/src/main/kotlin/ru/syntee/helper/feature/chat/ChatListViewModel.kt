package ru.syntee.helper.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.syntee.helper.core.data.chat.ChatRepository
import ru.syntee.helper.core.data.model.Chat
import javax.inject.Inject

/**
 * Список чатов: поиск по заголовкам, пин/архив/удаление/переименование.
 * Room — источник истины, все мутации через [ChatRepository].
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    val chats: StateFlow<List<Chat>> = combine(
        chatRepository.observeChats(),
        _query,
        _showArchived,
    ) { all, query, archived ->
        all.asSequence()
            .filter { it.isArchived == archived }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Событие «чат создан» → навигация в него. */
    private val _createdChat = Channel<String>(Channel.BUFFERED)
    val createdChat = _createdChat.receiveAsFlow()

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggleArchivedView() {
        _showArchived.value = !_showArchived.value
    }

    fun createChat() {
        viewModelScope.launch {
            _createdChat.send(chatRepository.createChat().id)
        }
    }

    fun setPinned(chat: Chat, pinned: Boolean) {
        viewModelScope.launch { chatRepository.setPinned(chat.id, pinned) }
    }

    fun setArchived(chat: Chat, archived: Boolean) {
        viewModelScope.launch { chatRepository.setArchived(chat.id, archived) }
    }

    fun renameChat(id: String, title: String) {
        viewModelScope.launch { chatRepository.renameChat(id, title) }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch { chatRepository.deleteChat(id) }
    }
}
