package ru.syntee.helper.feature.auth

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Скелет стадии 01.
 * Стадия 02: состояние ожидания браузера, обмен кода, ошибки, гостевой режим.
 */
@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel()
