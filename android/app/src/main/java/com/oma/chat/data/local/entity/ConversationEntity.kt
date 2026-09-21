package com.oma.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "conversations",
    primaryKeys = ["ownerUserId", "id"],
    indices = [
        Index(value = ["ownerUserId", "timestamp"])
    ]
)
data class ConversationEntity(
    val ownerUserId: String,
    val id: String,
    val name: String,
    val username: String? = null,
    val avatar: String,
    val lastMsg: String,
    val timestamp: Long,
    val type: String = "user",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L
)
