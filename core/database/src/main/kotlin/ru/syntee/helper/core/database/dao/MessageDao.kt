package ru.syntee.helper.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.syntee.helper.core.database.entity.MessageEntity

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeByChat(chatId: String): Flow<List<MessageEntity>>

    /** Последние N сообщений для контекста запроса (в хронологическом порядке). */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentDesc(chatId: String, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteByChat(chatId: String)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countAll(): Int

    @Query(
        "SELECT COALESCE(SUM(promptTokens), 0) AS prompt, " +
            "COALESCE(SUM(completionTokens), 0) AS completion FROM messages",
    )
    suspend fun tokenTotals(): TokenTotals

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getByChat(chatId: String): List<MessageEntity>
}

/** Агрегат токенов по всем сообщениям. */
data class TokenTotals(val prompt: Long, val completion: Long)
