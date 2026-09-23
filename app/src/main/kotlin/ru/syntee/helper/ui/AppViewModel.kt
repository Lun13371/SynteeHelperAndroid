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
import ru.syntee.helper.core.datastore.SettingsPreferences
import ru.syntee.helper.core.network.UrlProvider
import javax.inject.Inject

/**
 * Корневая VM: решает стартовый экран (логин/чаты) и применяет
 * URL-override-ы из настроек к сетевому слою.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val settings: SettingsPreferences,
    private val urlProvider: UrlProvider,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    init {
        // URL-override (dev-режим: LAN IP ПК для реального устройства).
        viewModelScope.launch {
            settings.settingsFlow.collect { s ->
                urlProvider.applyOverrides(s.apiBaseUrlOverride, s.webBaseUrlOverride)
            }
        }
    }
}
