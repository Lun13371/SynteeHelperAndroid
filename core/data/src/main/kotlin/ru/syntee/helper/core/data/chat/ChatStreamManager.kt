package ru.syntee.helper.core.data.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.Call
import ru.syntee.helper.core.data.model.ChatMessage
import ru.syntee.helper.core.data.model.ChatModel
import ru.syntee.helper.core.data.model.StreamingState
import ru.syntee.helper.core.datastore.SettingsPreferences
import ru.syntee.helper.core.database.entity.StreamingBufferEntity
import ru.syntee.helper.core.network.dto.ChatCompletionRequestDto
import ru.syntee.helper.core.network.dto.ChatMessagePayload
import ru.syntee.helper.core.network.sse.ChatHttpException
import ru.syntee.helper.core.network.sse.ChatStreamClient
import ru.syntee.helper.core.network.sse.StreamEvent
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер живых стримов (application-scope — переживает поворот экрана,
 * свёртывание приложения и навигацию между экранами).
 *
 * Пайплайн:
 * ```
 * user message (уже в Room) → POST /v1/chat/completions (SSE)
 *   → аккумулирование content/thinking (в т.ч. <think>-теги)
 *   → чекпоинт в streaming_buffers каждые 3с
 *   → [DONE] + usage → финальный MessageEntity в Room
 *   → отмена/ошибка → финализация с finishReason = cancelled|error
 * ```
 */
@Singleton
class ChatStreamManager @Inject constructor(
    private val chatRepository: ChatRepository,
    private val streamClient: ChatStreamClient,
    private val settings: SettingsPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val jobs = mutableMapOf<String, Job>()

    /** Живые HTTP-вызовы по chatId — для немедленного разрыва сокета при отмене. */
    private val calls = java.util.concurrent.ConcurrentHashMap<String, Call>()

    private val _states = MutableStateFlow<Map<String, StreamingState>>(emptyMap())
    val states: StateFlow<Map<String, StreamingState>> = _states.asStateFlow()

    /** Состояние конкретного чата (null — сейчас не стримится). */
    fun stateFor(chatId: String): kotlinx.coroutines.flow.Flow<StreamingState?> =
        states.map { it[chatId] }.distinctUntilChanged()

    /**
     * Запуск генерации. [requestMessages] — полная история контекста,
     * включая только что вставленное user-сообщение.
     */
    fun start(chatId: String, model: ChatModel, requestMessages: List<ChatMessage>) {
        synchronized(jobs) {
            if (jobs.containsKey(chatId)) return // уже стримится
            val job = scope.launch { runPipeline(chatId, model, requestMessages) }
            jobs[chatId] = job
            job.invokeOnCompletion {
                synchronized(jobs) { if (jobs[chatId] === job) jobs.remove(chatId) }
            }
        }
    }

    /**
     * Стоп-генерация: недописанный ответ сохраняется с finishReason = cancelled.
     *
     * ⚠️ Порядок важен: сначала отменяем корутину-коллектор (она висит на
     * cancellable-получении из канала → мгновенно финализируется), и только
     * потом рвём HTTP-вызов. Заблокированный readUtf8Line() НЕ реагирует на
     * отмену корутины — сокет закрывается только прямым call.cancel(),
     * иначе продюсер висит до конца стрима и блокирует новые генерации.
     */
    fun cancel(chatId: String) {
        synchronized(jobs) { jobs[chatId] }?.cancel()
        calls.remove(chatId)?.cancel()
    }

    val isStreaming: (String) -> Boolean = { chatId -> synchronized(jobs) { jobs.containsKey(chatId) } }

    // ── Пайплайн ────────────────────────────────────────────────────────────

    private suspend fun runPipeline(chatId: String, model: ChatModel, history: List<ChatMessage>) {
        val messageId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()

        val content = StringBuilder()
        val thinking = StringBuilder()
        val thinkTag = ThinkTagSplitter()
        val firstTokenAt = AtomicReference<Long?>(null)

        var promptTokens: Long? = null
        var completionTokens: Long? = null
        var finishReason: String? = null
        var error: String? = null
        var lastCheckpoint = 0L

        publish(chatId) {
            StreamingState(chatId = chatId, messageId = messageId, model = model.id, startedAt = startedAt)
        }

        try {
            val s = settings.current()
            val request = ChatCompletionRequestDto(
                model = model.id,
                messages = history.map { ChatMessagePayload(role = it.role.name.lowercase(), content = it.content) },
                temperature = s.temperature,
                max_tokens = s.maxTokens.toLong(),
                reasoning_effort = s.reasoningEffort
                    .takeIf { it != "off" && model.reasoningSupported },
            )

            streamClient.stream(request, onCall = { call -> calls[chatId] = call }).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        if (firstTokenAt.get() == null) firstTokenAt.set(System.currentTimeMillis())
                        thinkTag.split(event.content, content, thinking)
                        event.thinking?.let { thinking.append(it) }
                        publishSnapshot(chatId, messageId, model.id, startedAt, firstTokenAt.get(), content, thinking, error = null)
                    }
                    is StreamEvent.Usage -> {
                        promptTokens = event.promptTokens
                        completionTokens = event.completionTokens
                    }
                    is StreamEvent.Finish -> finishReason = event.reason
                    is StreamEvent.Done -> Unit // финализация ниже
                }
                // Чекпоинт каждые 3 секунды стрима
                val now = System.currentTimeMillis()
                if (now - startedAt - lastCheckpoint >= CHECKPOINT_INTERVAL_MS) {
                    lastCheckpoint = now - startedAt
                    chatRepository.checkpoint(
                        StreamingBufferEntity(
                            messageId = messageId,
                            chatId = chatId,
                            content = content.toString(),
                            thinking = thinking.toString(),
                            model = model.id,
                            startedAt = startedAt,
                            updatedAt = now,
                        ),
                    )
                }
            }
            if (finishReason == null) finishReason = "stop"
        } catch (e: CancellationException) {
            finishReason = "cancelled"
            throw e // даём корутине завершиться, финализация — в finally (NonCancellable)
        } catch (e: ChatHttpException) {
            finishReason = "error"
            error = e.message
        } catch (e: Throwable) {
            // Гонка отмены: call.cancel() рвёт сокет → IOException из blocked read
            // может обогнать CancellationException. Если корутина уже отменена —
            // это отмена, а не ошибка.
            if (!currentCoroutineContext().isActive) {
                finishReason = "cancelled"
            } else {
                finishReason = "error"
                error = e.message ?: "Не удалось получить ответ"
            }
        } finally {
            calls.remove(chatId)
            // Финализация выполняется даже при отмене корутины.
            // runCatching: даже если финализация упадёт (например, чат удалён),
            // состояние стрима обязано быть очищено — иначе UI зависнет.
            withContext(NonCancellable) {
                runCatching {
                    chatRepository.finalizeAssistantMessage(
                        chatId = chatId,
                        messageId = messageId,
                        content = content.toString(),
                        thinking = thinking.toString(),
                        model = model.id,
                        modelProvider = model.ownedBy,
                        startedAt = startedAt,
                        firstTokenAt = firstTokenAt.get(),
                        durationMs = System.currentTimeMillis() - startedAt,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        finishReason = finishReason,
                    )
                }
            }
            // Короткая задержка состояния ошибки, чтобы UI успел показать «Повторить»
            publish(chatId) {
                StreamingState(
                    chatId = chatId,
                    messageId = messageId,
                    content = content.toString(),
                    thinking = thinking.toString(),
                    model = model.id,
                    startedAt = startedAt,
                    firstTokenAt = firstTokenAt.get(),
                    error = error,
                    finishReason = finishReason,
                )
            }
            if (error != null) {
                withContext(NonCancellable) { kotlinx.coroutines.delay(STATE_TTL_MS) }
            }
            _states.update { it - chatId }
        }
    }

    private inline fun publish(chatId: String, crossinline build: () -> StreamingState) {
        _states.update { it + (chatId to build()) }
    }

    private fun publishSnapshot(
        chatId: String,
        messageId: String,
        model: String,
        startedAt: Long,
        firstTokenAt: Long?,
        content: StringBuilder,
        thinking: StringBuilder,
        error: String?,
    ) {
        _states.update {
            it + (chatId to StreamingState(
                chatId = chatId,
                messageId = messageId,
                content = content.toString(),
                thinking = thinking.toString(),
                model = model,
                startedAt = startedAt,
                firstTokenAt = firstTokenAt,
                error = error,
            ))
        }
    }

    private companion object {
        const val CHECKPOINT_INTERVAL_MS = 3_000L
        const val STATE_TTL_MS = 4_000L
    }
}

/**
 * Разделяет `<think>…</think>`-теги в стримящемся content:
 * внутренности тега уходят в thinking, остальное — в content.
 * Тег может приходить частями — состояние сохраняется между чанками.
 */
private class ThinkTagSplitter {
    private var insideThink = false

    fun split(chunk: String?, content: StringBuilder, thinking: StringBuilder) {
        if (chunk.isNullOrEmpty()) return
        var rest: String = chunk
        while (rest.isNotEmpty()) {
            if (insideThink) {
                val end = rest.indexOf(CLOSE_TAG)
                when {
                    end >= 0 -> {
                        thinking.append(rest, 0, end)
                        rest = rest.substring(end + CLOSE_TAG.length)
                        insideThink = false
                    }
                    else -> {
                        thinking.append(rest)
                        rest = ""
                    }
                }
            } else {
                val start = rest.indexOf(OPEN_TAG)
                when {
                    start >= 0 -> {
                        content.append(rest, 0, start)
                        rest = rest.substring(start + OPEN_TAG.length)
                        insideThink = true
                    }
                    else -> {
                        content.append(rest)
                        rest = ""
                    }
                }
            }
        }
    }

    private companion object {
        const val OPEN_TAG = "<think>"
        const val CLOSE_TAG = "</think>"
    }
}
