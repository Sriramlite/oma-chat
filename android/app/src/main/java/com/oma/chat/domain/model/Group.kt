package com.oma.chat.domain.model

data class Group(
    val id: String,
    val name: String,
    val avatar: String,
    val adminIds: List<String> = emptyList(),
    val members: List<String> = emptyList(),
    val created: Long = 0L,
    val lastMsg: String? = null,
    val lastTimestamp: Long? = null
) {
    fun isAdmin(userId: String): Boolean = adminIds.contains(userId)
    fun isMember(userId: String): Boolean = members.contains(userId)
}
