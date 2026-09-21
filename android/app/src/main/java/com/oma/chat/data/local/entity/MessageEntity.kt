package com.oma.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "messages",
    primaryKeys = ["ownerUserId", "id"],
    indices = [
        Index(value = ["ownerUserId", "chatId", "timestamp"]),
        Index(value = ["ownerUserId", "tempId"])
    ]
)
data class MessageEntity(
    val ownerUserId: String,
    val id: String,
    val tempId: String?,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val avatar: String,
    val content: String,
    val type: String,
    val timestamp: Long,
    val status: String,
    val replyToId: String? = null,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false
)
