package ru.syntee.helper.core.data.model

import kotlinx.serialization.Serializable

/** Доменная модель модели gateway (упрощение GatewayModelDto для UI). */
@Serializable
data class ChatModel(
    val id: String,
    val displayName: String,
    val ownedBy: String? = null,
    val supportsStreaming: Boolean = true,
    val supportsTools: Boolean = false,
    val supportsVision: Boolean = false,
    val reasoningSupported: Boolean = false,
    val reasoningLevels: List<String> = emptyList(),
    val maxTokens: Long? = null,
    val description: String? = null,
    /** За 1k токенов (UI умножает ×1000 → за 1M). */
    val inputPricePer1k: Double? = null,
    val outputPricePer1k: Double? = null,
    val status: ModelStatus = ModelStatus.UNKNOWN,
)

@Serializable
enum class ModelStatus {
    OPERATIONAL, DEGRADED, DOWN, AVAILABLE, UNAVAILABLE, UNKNOWN;

    companion object {
        fun from(raw: String?): ModelStatus = when (raw?.lowercase()) {
            "operational" -> OPERATIONAL
            "degraded" -> DEGRADED
            "down" -> DOWN
            "available" -> AVAILABLE
            "unavailable" -> UNAVAILABLE
            else -> UNKNOWN
        }
    }
}

@Serializable
data class GatewayUser(
    val id: String,
    val email: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val role: String? = null,
)

/** Состояние каталога моделей (кэш + загрузка + ошибка). */
sealed interface ModelsState {
    data object Loading : ModelsState
    data class Loaded(val models: List<ChatModel>, val fromCache: Boolean = false) : ModelsState
    data class Error(val message: String, val cached: List<ChatModel> = emptyList()) : ModelsState
}
