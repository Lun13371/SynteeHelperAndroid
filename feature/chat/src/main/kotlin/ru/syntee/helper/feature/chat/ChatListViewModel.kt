package ru.syntee.helper.feature.chat

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Скелет стадии 01.
 * Стадия 03: список чатов из Room (Flow), пин/архив/удаление, поиск.
 */
@HiltViewModel
class ChatListViewModel @Inject constructor() : ViewModel()
