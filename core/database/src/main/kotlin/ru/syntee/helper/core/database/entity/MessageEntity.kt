package ru.syntee.helper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сообщение чата. role: user | assistant | system. */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chatId"),
        Index("createdAt"),
        Index(value = ["chatId", "createdAt", "id"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,              // UUID
    val chatId: String,
    val role: String,                        // user | assistant | system
    val content: String,
    val createdAt: Long,
    val model: String? = null,
    val thinking: String? = null,            // reasoning-текст
    val thinkingMs: Long? = null,
    val durationMs: Long? = null,
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val finishReason: String? = null,        // stop | length | error | cancelled | interrupted
)
