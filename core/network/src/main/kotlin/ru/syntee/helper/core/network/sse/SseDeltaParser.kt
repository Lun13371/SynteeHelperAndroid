package ru.syntee.helper.core.network.sse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * События SSE-стрима /v1/chat/completions.
 */
sealed interface StreamEvent {
    /** Дельта ответа: content и/или thinking (могут прийти в одном чанке). */
    data class Delta(val content: String?, val thinking: String?) : StreamEvent

    /** usage-чанк (при stream_options.include_usage — последний перед [DONE]). */
    data class Usage(val promptTokens: Long?, val completionTokens: Long?) : StreamEvent

    /** finish_reason из choices[0] (stop | length | ...). */
    data class Finish(val reason: String) : StreamEvent

    /** Маркер [DONE] — сервер закончил поток. */
    data object Done : StreamEvent
}

/**
 * Толерантный парсер SSE-данных gateway.
 *
 * ⚠️ Reasoning-дельты приходят в 3+ форматах (проверено по коду gateway):
 * ```
 * delta.reasoning_content: "..."            // строка
 * delta.reasoning: { text: "..." }          // объект
 * delta.reasoning: "..."                    // строка
 * delta.thinking: "..." | { text: "..." }
 * delta.content: [ { type: "text", text: "..." }, ... ]   // массив частей
 * delta.content: { text: "..." } | { content: "..." }
 * ```
 * Плюс `<think>...</think>`-теги внутри content (разбирает ChatStreamManager).
 */
object SseDeltaParser {

    /** @return null — служебный чанк без полезной нагрузки. */
    fun parse(json: Json, data: String): StreamEvent? {
        if (data == DONE_MARKER) return StreamEvent.Done

        val root = runCatching { json.parseToJsonElement(data).jsonObject }
            .getOrElse { return null }

        // usage может прийти отдельным чанком без choices
        root["usage"]?.let { u ->
            if (u !is JsonNull) {
                val obj = runCatching { u.jsonObject }.getOrNull() ?: return null
                return StreamEvent.Usage(
                    promptTokens = obj.long("prompt_tokens"),
                    completionTokens = obj.long("completion_tokens"),
                )
            }
        }

        val choices = (root["choices"] as? JsonArray) ?: return null
        val choice = choices.firstOrNull() as? JsonObject ?: return null

        choice["finish_reason"]?.let { f ->
            if (f !is JsonNull) {
                val reason = (f as? JsonPrimitive)?.content
                if (!reason.isNullOrEmpty()) return StreamEvent.Finish(reason)
            }
        }

        val delta = choice["delta"] as? JsonObject ?: return null

        val content = extractText(delta["content"])
        val thinking = extractText(delta["reasoning_content"])
            ?: extractText(delta["reasoning"])
            ?: extractText(delta["thinking"])

        return if (content != null || thinking != null) {
            StreamEvent.Delta(content = content, thinking = thinking)
        } else {
            null // role-чанк и прочие служебные
        }
    }

    /**
     * Достаёт текст из элемента любого формата:
     * строка / массив частей / объект {text}|{content}|{thinking}.
     */
    private fun extractText(element: Any?): String? = when (element) {
        null, is JsonNull -> null
        is JsonPrimitive -> element.content.takeIf { it.isNotEmpty() }
        is JsonArray -> element.joinToString(separator = "") { extractText(it) ?: "" }
            .takeIf { it.isNotEmpty() }
        is JsonObject ->
            (extractText(element["text"])
                ?: extractText(element["content"])
                ?: extractText(element["thinking"]))
        else -> null
    }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private const val DONE_MARKER = "[DONE]"
}
