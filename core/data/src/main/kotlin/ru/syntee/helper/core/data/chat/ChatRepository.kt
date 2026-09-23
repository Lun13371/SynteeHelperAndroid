package ru.syntee.helper.core.data.chat

import ru.syntee.helper.core.data.model.Chat
import ru.syntee.helper.core.data.model.ChatMessage
import ru.syntee.helper.core.data.model.MessageRole
import ru.syntee.helper.core.database.dao.ChatDao
import ru.syntee.helper.core.database.dao.MessageDao
import ru.syntee.helper.core.database.dao.StreamingBufferDao
import ru.syntee.helper.core.database.entity.ChatEntity
import ru.syntee.helper.core.database.entity.MessageEntity
import ru.syntee.helper.core.database.entity.StreamingBufferEntity
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище чатов: Room — источник истины для UI.
 * Стриминг-пайплайн живёт в [ChatStreamManager], сюда пишет только финалы.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val streamingBufferDao: StreamingBufferDao,
) {

    // ── Наблюдение ──────────────────────────────────────────────────────────

    fun observeChats() = chatDao.observeAll().toDomainChats()

    fun observeMessages(chatId: String) = messageDao.observeByChat(chatId).toDomainMessages()

    // ── CRUD чатов ──────────────────────────────────────────────────────────

    suspend fun createChat(title: String = "Новый чат"): Chat {
        val now = System.currentTimeMillis()
        val entity = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
        )
        chatDao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun getChat(id: String): Chat? = chatDao.getById(id)?.toDomain()

    suspend fun renameChat(id: String, title: String) {
        chatDao.rename(id, title.trim().ifEmpty { "Без названия" }, System.currentTimeMillis())
    }

    suspend fun setPinned(id: String, pinned: Boolean) = chatDao.setPinned(id, pinned)

    suspend fun setArchived(id: String, archived: Boolean) =
        chatDao.setArchived(id, archived, System.currentTimeMillis())

    suspend fun deleteChat(id: String) = chatDao.delete(id)

    /** Модель, закреплённая за чатом (без обновления updatedAt). */
    suspend fun setChatModel(id: String, modelId: String?, provider: String?) =
        chatDao.setModel(id, modelId, provider)

    /** Чистка пустых чатов при старте приложения (FK-гонки нет: upsert в sendMessage). */
    suspend fun deleteEmptyChats() = chatDao.deleteEmpty()

    // ── Сообщения ───────────────────────────────────────────────────────────

    /**
     * Записывает user-сообщение. Если чата нет — пересоздаёт с тем же id
     * (защита от FK-гонки с deleteEmptyChats). Первое сообщение задаёт
     * заголовок чата (~40 символов, как на десктопе).
     */
    suspend fun insertUserMessage(chatId: String, text: String): ChatMessage {
        if (chatDao.getById(chatId) == null) {
            val now = System.currentTimeMillis()
            chatDao.upsert(
                ChatEntity(id = chatId, title = "Новый чат", createdAt = now, updatedAt = now),
            )
        }
        val now = System.currentTimeMillis()
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = MessageRole.USER.name.lowercase(),
            content = text,
            createdAt = now,
        )
        messageDao.upsert(entity)
        chatDao.updateActivity(chatId, now, modelId = null, provider = null)

        // Авто-заголовок из первого сообщения (как на десктопе)
        if (chatDao.countMessages(chatId) == 1) {
            val title = text.lineSequence().firstOrNull()?.take(40)?.trim().orEmpty()
            if (title.isNotEmpty()) chatDao.rename(chatId, title, now)
        }
        return entity.toDomain()
    }

    /** История для контекста запроса: последние [limit] сообщений (хронология). */
    suspend fun getRecentMessages(chatId: String, limit: Int = 40): List<ChatMessage> =
        messageDao.getRecentDesc(chatId, limit).reversed().map { it.toDomain() }

    suspend fun deleteMessage(id: String) = messageDao.delete(id)

    // ── Стриминг: чекпоинты / финализация / рекавери ────────────────────────

    /** Чекпоинт живого стрима (каждые 3с) — крэш-рекавери. */
    suspend fun checkpoint(buffer: StreamingBufferEntity) = streamingBufferDao.upsert(buffer)

    /** Финал стрима: сообщение в Room + чистка буфера + обновление чата. */
    suspend fun finalizeAssistantMessage(
        chatId: String,
        messageId: String,
        content: String,
        thinking: String?,
        model: String?,
        modelProvider: String?,
        startedAt: Long,
        firstTokenAt: Long?,
        durationMs: Long?,
        promptTokens: Long?,
        completionTokens: Long?,
        finishReason: String?,
    ) {
        val now = System.currentTimeMillis()
        messageDao.upsert(
            MessageEntity(
                id = messageId,
                chatId = chatId,
                role = MessageRole.ASSISTANT.name.lowercase(),
                content = content,
                createdAt = now,
                model = model,
                thinking = thinking?.takeIf { it.isNotBlank() },
                thinkingMs = firstTokenAt?.let { it - startedAt },
                durationMs = durationMs ?: (now - startedAt),
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                finishReason = finishReason,
            ),
        )
        streamingBufferDao.deleteByChat(chatId)
        chatDao.updateActivity(chatId, now, model, modelProvider)
    }

    /**
     * Крэш-рекавери при старте: недописанные стримы → финализировать
     * с finishReason = "interrupted", буферы чистить.
     */
    suspend fun recoverStreaming(): Int {
        val buffers = streamingBufferDao.getAll()
        for (b in buffers) {
            messageDao.upsert(
                MessageEntity(
                    id = b.messageId,
                    chatId = b.chatId,
                    role = MessageRole.ASSISTANT.name.lowercase(),
                    content = b.content,
                    createdAt = b.updatedAt,
                    model = b.model,
                    thinking = b.thinking?.takeIf { it.isNotBlank() },
                    thinkingMs = null,
                    durationMs = b.updatedAt - b.startedAt,
                    finishReason = "interrupted",
                ),
            )
            streamingBufferDao.deleteByChat(b.chatId)
        }
        return buffers.size
    }

    // ── Маппинг ─────────────────────────────────────────────────────────────

    private fun ChatEntity.toDomain() = Chat(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
        isArchived = isArchived,
        modelId = modelId,
        modelProvider = modelProvider,
        reasoningValue = reasoningValue,
    )

    private fun MessageEntity.toDomain() = ChatMessage(
        id = id,
        chatId = chatId,
        role = when (role.lowercase()) {
            "user" -> MessageRole.USER
            "system" -> MessageRole.SYSTEM
            else -> MessageRole.ASSISTANT
        },
        content = content,
        createdAt = createdAt,
        model = model,
        thinking = thinking,
        thinkingMs = thinkingMs,
        durationMs = durationMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        finishReason = finishReason,
    )

    private fun kotlinx.coroutines.flow.Flow<List<ChatEntity>>.toDomainChats() =
        map { list -> list.map { it.toDomain() } }

    private fun kotlinx.coroutines.flow.Flow<List<MessageEntity>>.toDomainMessages() =
        map { list -> list.map { it.toDomain() } }
}
