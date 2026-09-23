package ru.syntee.helper.core.network.dto

import kotlinx.serialization.Serializable

/** Запрос POST /v1/chat/completions (OpenAI-совместимый формат gateway). */
@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessagePayload>,
    val temperature: Double? = null,
    val max_tokens: Long? = null,
    val stream: Boolean = true,
    val stream_options: StreamOptionsDto? = StreamOptionsDto(),
    val reasoning_effort: String? = null,
)

@Serializable
data class ChatMessagePayload(
    val role: String,        // user | assistant | system
    val content: String,
)

@Serializable
data class StreamOptionsDto(val include_usage: Boolean = true)
