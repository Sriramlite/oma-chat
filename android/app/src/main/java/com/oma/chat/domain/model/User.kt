package com.oma.chat.domain.model

data class User(
    val id: String,
    val username: String,
    val name: String,
    val avatar: String,
    val bio: String? = null,
    val lastSeen: Long = 0L,
    val phone: String? = null,
    val isBlocked: Boolean = false
)

data class AuthSession(
    val token: String,
    val user: User,
    val isNew: Boolean = false
)
