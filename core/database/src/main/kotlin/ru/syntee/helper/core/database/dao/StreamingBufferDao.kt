package ru.syntee.helper.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.syntee.helper.core.database.entity.StreamingBufferEntity

@Dao
interface StreamingBufferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(buffer: StreamingBufferEntity)

    @Query("SELECT * FROM streaming_buffers WHERE chatId = :chatId LIMIT 1")
    suspend fun getByChat(chatId: String): StreamingBufferEntity?

    @Query("SELECT * FROM streaming_buffers")
    suspend fun getAll(): List<StreamingBufferEntity>

    @Query("DELETE FROM streaming_buffers WHERE chatId = :chatId")
    suspend fun deleteByChat(chatId: String)

    @Query("DELETE FROM streaming_buffers")
    suspend fun clear()
}
