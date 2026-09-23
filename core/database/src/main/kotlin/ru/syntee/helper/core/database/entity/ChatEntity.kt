package ru.syntee.helper.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Чат (схема повторяет десктоп, упрощено до базы). */
@Entity(tableName = "chats", indices = [Index("updatedAt"), Index("isArchived")])
data class ChatEntity(
    @PrimaryKey val id: String,              // UUID
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val color: String? = null,               // кастомный цвет чата (стадия 05)
    val modelId: String? = null,             // последняя модель чата
    val modelProvider: String? = null,
    val reasoningValue: String? = null,      // reasoning_effort последнего запроса
)
