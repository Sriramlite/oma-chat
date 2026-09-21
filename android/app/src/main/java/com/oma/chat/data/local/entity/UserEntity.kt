package com.oma.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    val name: String,
    val avatar: String,
    val bio: String? = null,
    val lastSeen: Long = 0L,
    val isBlocked: Boolean = false
)
