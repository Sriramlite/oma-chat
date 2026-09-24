package com.oma.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.oma.chat.data.local.dao.ConversationDao
import com.oma.chat.data.local.dao.GroupDao
import com.oma.chat.data.local.dao.MessageDao
import com.oma.chat.data.local.dao.SyncQueueDao
import com.oma.chat.data.local.dao.UserDao
import com.oma.chat.data.local.entity.ConversationEntity
import com.oma.chat.data.local.entity.GroupEntity
import com.oma.chat.data.local.entity.MessageEntity
import com.oma.chat.data.local.entity.SyncQueueEntity
import com.oma.chat.data.local.entity.UserEntity

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        SyncQueueEntity::class,
        UserEntity::class,
        GroupEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OmaDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao

    companion object {
        const val DATABASE_NAME = "oma_chat_nxtgen.db"
    }
}
