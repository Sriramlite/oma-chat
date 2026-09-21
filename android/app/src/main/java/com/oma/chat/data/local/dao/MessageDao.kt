package com.oma.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oma.chat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE ownerUserId = :ownerUserId AND chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(ownerUserId: String, chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE ownerUserId = :ownerUserId AND id = :messageId LIMIT 1")
    suspend fun getMessageById(ownerUserId: String, messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE ownerUserId = :ownerUserId AND tempId = :tempId LIMIT 1")
    suspend fun getMessageByTempId(ownerUserId: String, tempId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun updateMessageStatus(ownerUserId: String, id: String, status: String)

    @Query("UPDATE messages SET content = :content, isEdited = :isEdited WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun updateMessageContent(ownerUserId: String, id: String, content: String, isEdited: Boolean = true)

    @Query("UPDATE messages SET isStarred = :isStarred WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun updateMessageStarred(ownerUserId: String, id: String, isStarred: Boolean)

    @Query("UPDATE messages SET isPinned = :isPinned WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun updateMessagePinned(ownerUserId: String, id: String, isPinned: Boolean)

    @Query("UPDATE messages SET isDeleted = 1, content = :placeholder, type = 'system' WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun deleteMessageForEveryone(ownerUserId: String, id: String, placeholder: String = "🚫 This message was deleted")

    @Query("DELETE FROM messages WHERE ownerUserId = :ownerUserId AND id = :id")
    suspend fun deleteMessage(ownerUserId: String, id: String)

    @Query("DELETE FROM messages WHERE ownerUserId = :ownerUserId AND chatId = :chatId")
    suspend fun deleteMessagesForChat(ownerUserId: String, chatId: String)

    @Query("DELETE FROM messages WHERE ownerUserId = :ownerUserId")
    suspend fun clearAllMessagesForUser(ownerUserId: String)
}
