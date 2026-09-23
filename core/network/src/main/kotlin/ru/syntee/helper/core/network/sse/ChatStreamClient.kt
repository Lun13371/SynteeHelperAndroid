package ru.syntee.helper.core.network.sse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.syntee.helper.core.network.AppInfo
import ru.syntee.helper.core.network.di.GatewayClient
import ru.syntee.helper.core.network.dto.ChatCompletionRequestDto
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** HTTP-ошибка /v1/chat/completions (не-SSE ответ). */
class ChatHttpException(val code: Int, message: String) : Exception(message)

/**
 * SSE-клиент стриминга чата: POST /v1/chat/completions (stream: true).
 *
 * — Наследует интерцепторы gateway-клиента (BaseUrl / Identity / Auth+refresh),
 *   но снимает read/call-таймауты: стрим может длиться минуты.
 * — Отмена коллектора (viewModelScope / cancelGeneration) отменяет HTTP-вызов.
 * — Ручной построчный SSE-парсинг: надёжная backpressure без потерь дельт.
 */
@Singleton
class ChatStreamClient @Inject constructor(
    @GatewayClient baseClient: OkHttpClient,
    private val json: Json,
    private val appInfo: AppInfo,
) {

    private val sseClient: OkHttpClient = baseClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun stream(request: ChatCompletionRequestDto): Flow<StreamEvent> = flow {
        val payload = json.encodeToString(ChatCompletionRequestDto.serializer(), request)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        // URL строим от дефолта сборки — BaseUrlInterceptor подменит на эффективный.
        val url = appInfo.defaultApiUrl.removeSuffix("/") + "/v1/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .post(payload)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val call = sseClient.newCall(httpRequest)
        // Отмена flow → отмена вызова: blocked read прервётся IOException.
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorBody = runCatching { response.body?.string() }.getOrNull()
                response.close()
                throw ChatHttpException(response.code, parseErrorDetail(errorBody))
            }
            val source = response.body?.source()
            if (source == null) {
                response.close()
                throw IOException("Пустой ответ сервера")
            }

            val dataBuffer = StringBuilder()
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("data:") ->
                            dataBuffer.append(line.removePrefix("data:").removePrefix(" ")).append('\n')

                        line.isEmpty() -> {
                            val data = dataBuffer.toString().trimEnd('\n')
                            dataBuffer.clear()
                            if (data.isNotEmpty()) {
                                val event = SseDeltaParser.parse(json, data)
                                if (event != null) emit(event)
                                if (event is StreamEvent.Done) return@flow
                            }
                        }
                        // "event:" / "id:" / ":" (комментарий) — игнорируем
                    }
                }
            } finally {
                response.close()
            }
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** gateway шлёт ошибки как {"detail": "..."} — вытаскиваем для UI. */
    private fun parseErrorDetail(body: String?): String {
        if (body.isNullOrBlank()) return "Ошибка сервера"
        val detail = runCatching {
            json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
        return detail ?: body.take(200)
    }
}
