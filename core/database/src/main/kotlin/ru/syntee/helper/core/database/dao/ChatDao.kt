package ru.syntee.helper.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.syntee.helper.core.database.entity.ChatEntity

@Dao
interface ChatDao {

    /** Все чаты (активные + архив) — фильтрация в репозитории/VM. */
    @Query("SELECT * FROM chats ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getById(id: String): ChatEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :id")
    suspend fun countMessages(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity)

    @Query("UPDATE chats SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE chats SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE chats SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE chats SET updatedAt = :updatedAt, modelId = :modelId, modelProvider = :provider WHERE id = :id")
    suspend fun updateActivity(id: String, updatedAt: Long, modelId: String?, provider: String?)

    @Query("UPDATE chats SET modelId = :modelId, modelProvider = :provider WHERE id = :id")
    suspend fun setModel(id: String, modelId: String?, provider: String?)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun delete(id: String)

    /** Чистка пустых чатов (без сообщений) при старте приложения. */
    @Query("DELETE FROM chats WHERE id NOT IN (SELECT DISTINCT chatId FROM messages)")
    suspend fun deleteEmpty()
}
