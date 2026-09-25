package com.oma.chat.domain.model

data class UserPrivacySettings(
    val lastSeenPrivacy: String = "everyone",
    val profilePhotoPrivacy: String = "everyone",
    val aboutPrivacy: String = "everyone",
    val phonePrivacy: String = "everyone",
    val readReceipts: Boolean = true,
    val shareBattery: Boolean = true,
    val wallpaper: String = "bookshelf"
)

data class User(
    val id: String,
    val username: String,
    val name: String,
    val avatar: String,
    val bio: String? = null,
    val lastSeen: Long = 0L,
    val phone: String? = null,
    val isBlocked: Boolean = false,
    val battery: Int? = null,
    val isCharging: Boolean = false,
    val settings: UserPrivacySettings = UserPrivacySettings(),
    val blockedUsers: List<String> = emptyList()
)

data class AuthSession(
    val token: String,
    val user: User,
    val isNew: Boolean = false
)
