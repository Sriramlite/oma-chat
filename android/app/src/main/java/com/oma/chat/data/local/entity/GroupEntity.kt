package com.oma.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatar: String,
    val adminIds: List<String>,
    val members: List<String>,
    val created: Long
)
