package ru.syntee.helper.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.syntee.helper.core.database.dao.ChatDao
import ru.syntee.helper.core.database.dao.MessageDao
import ru.syntee.helper.core.database.dao.StreamingBufferDao
import ru.syntee.helper.core.database.entity.ChatEntity
import ru.syntee.helper.core.database.entity.MessageEntity
import ru.syntee.helper.core.database.entity.StreamingBufferEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        StreamingBufferEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun streamingBufferDao(): StreamingBufferDao
}
