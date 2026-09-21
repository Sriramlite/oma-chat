package com.oma.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["ownerUserId", "createdAt"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    val tempId: String,
    val ownerUserId: String,
    val receiverId: String,
    val content: String,
    val type: String = "text",
    val replyToId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
