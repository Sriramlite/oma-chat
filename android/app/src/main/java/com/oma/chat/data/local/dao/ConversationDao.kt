package com.oma.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oma.chat.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId ORDER BY timestamp DESC")
    fun getConversations(ownerUserId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId AND id = :id LIMIT 1")
    suspend fun getConversationById(ownerUserId: String, id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun clearUnreadCount(ownerUserId: String, id: String)

    @Query("UPDATE conversations SET isOnline = :isOnline, lastSeen = :lastSeen WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun updatePresence(ownerUserId: String, id: String, isOnline: Boolean, lastSeen: Long)

    @Query("DELETE FROM conversations WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun deleteConversation(ownerUserId: String, id: String)

    @Query("DELETE FROM conversations WHERE ownerUserId = :ownerUserId")
    suspend fun clearConversationsForUser(ownerUserId: String)
}
