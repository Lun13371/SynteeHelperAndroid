package ru.syntee.helper.core.data.model

/** Чат (доменная модель над ChatEntity). */
data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val modelId: String? = null,
    val modelProvider: String? = null,
    val reasoningValue: String? = null,
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

/** Сообщение чата (финализированное — из Room). */
data class ChatMessage(
    val id: String,
    val chatId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    val model: String? = null,
    val thinking: String? = null,
    val thinkingMs: Long? = null,
    val durationMs: Long? = null,
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val finishReason: String? = null,
)

/**
 * Живое состояние стриминга чата (из ChatStreamManager).
 * Пока стрим идёт — UI подмешивает его к сообщениям из Room.
 */
data class StreamingState(
    val chatId: String,
    val messageId: String,
    val content: String = "",
    val thinking: String = "",
    val model: String? = null,
    val startedAt: Long = 0L,
    val firstTokenAt: Long? = null,
    /** Не null — стрим упал (UI предложит «Повторить»). */
    val error: String? = null,
    val finishReason: String? = null,
) {
    val isBlank: Boolean get() = content.isBlank() && thinking.isBlank()
}
