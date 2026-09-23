package ru.syntee.helper.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Чекпоинт живого стрима (каждые 3с) — крэш-рекавери:
 * при старте приложения недописанные ответы финализируются
 * с finishReason = "interrupted", буферы чистятся.
 */
@Entity(tableName = "streaming_buffers")
data class StreamingBufferEntity(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val content: String,
    val thinking: String? = null,
    val model: String? = null,
    val startedAt: Long,
    val updatedAt: Long,
)
