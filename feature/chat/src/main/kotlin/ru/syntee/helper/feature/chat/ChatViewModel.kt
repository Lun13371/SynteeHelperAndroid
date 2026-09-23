package ru.syntee.helper.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Скелет стадии 01.
 * Стадия 03: стриминг SSE, состояние генерации, автоскролл, отмена.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Идентификатор чата из маршрута ChatRoute (type-safe nav). */
    val chatId: String = savedStateHandle.get<String>("chatId").orEmpty()
}
