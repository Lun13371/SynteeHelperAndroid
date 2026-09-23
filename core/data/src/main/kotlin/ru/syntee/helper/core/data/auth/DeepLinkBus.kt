package ru.syntee.helper.core.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Мост deep link → ViewModel.
 *
 * MainActivity ловит intent synteehelper://auth/callback?code=... и кладёт
 * код сюда; LoginViewModel (живой в этот момент) забирает его и обменивает.
 * Код одноразовый с TTL 10 секунд — обрабатывается немедленно.
 */
@Singleton
class DeepLinkBus @Inject constructor() {

    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()

    fun submitCode(code: String) {
        _code.value = code
    }

    /** Вызывается после обработки (успех/ошибка), чтобы не реиграть. */
    fun consume() {
        _code.value = null
    }
}
