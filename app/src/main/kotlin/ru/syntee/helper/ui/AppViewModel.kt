package ru.syntee.helper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.syntee.helper.core.data.auth.AuthRepository
import ru.syntee.helper.core.data.auth.AuthState
import ru.syntee.helper.core.data.chat.ChatRepository
import ru.syntee.helper.core.datastore.SettingsPreferences
import ru.syntee.helper.core.network.UrlProvider
import javax.inject.Inject

/**
 * Корневая VM: решает стартовый экран (логин/чаты), применяет
 * URL-override-ы из настроек к сетевому слою и запускает
 * крэш-рекавери стримов при старте приложения.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val settings: SettingsPreferences,
    private val urlProvider: UrlProvider,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    init {
        // Прод-режим + URL-override (dev: LAN IP ПК для реального устройства)
        // применяются к сетевому слою немедленно при изменении настроек.
        viewModelScope.launch {
            settings.settingsFlow.collect { s ->
                urlProvider.applySettings(
                    production = s.productionMode,
                    api = s.apiBaseUrlOverride,
                    web = s.webBaseUrlOverride,
                )
            }
        }

        // Стадия 03: крэш-рекавери — недописанные стримы финализируются,
        // пустые чаты (открыл и ушёл) чистятся.
        viewModelScope.launch {
            runCatching { chatRepository.recoverStreaming() }
            runCatching { chatRepository.deleteEmptyChats() }
        }
    }
}
