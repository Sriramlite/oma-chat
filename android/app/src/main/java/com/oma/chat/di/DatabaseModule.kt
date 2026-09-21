package com.oma.chat.di

import android.content.Context
import androidx.room.Room
import com.oma.chat.data.local.OmaDatabase
import com.oma.chat.data.local.dao.ConversationDao
import com.oma.chat.data.local.dao.GroupDao
import com.oma.chat.data.local.dao.MessageDao
import com.oma.chat.data.local.dao.SyncQueueDao
import com.oma.chat.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOmaDatabase(
        @ApplicationContext context: Context
    ): OmaDatabase {
        return Room.databaseBuilder(
            context,
            OmaDatabase::class.java,
            OmaDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMessageDao(database: OmaDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideConversationDao(database: OmaDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideUserDao(database: OmaDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideGroupDao(database: OmaDatabase): GroupDao {
        return database.groupDao()
    }

    @Provides
    fun provideSyncQueueDao(database: OmaDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }
}
