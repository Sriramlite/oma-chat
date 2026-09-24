package com.oma.chat.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.oma.chat.domain.model.User

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class SignupRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String
)

data class GoogleAuthRequest(
    @SerializedName("idToken") val idToken: String
)

data class PhoneAuthRequest(
    @SerializedName("idToken") val idToken: String
)

data class CompleteProfileRequest(
    @SerializedName("username") val username: String,
    @SerializedName("phone") val phone: String
)

data class ForgotPasswordRequest(
    @SerializedName("username") val username: String
)

data class ResetPasswordRequest(
    @SerializedName("username") val username: String,
    @SerializedName("otp") val otp: String,
    @SerializedName("newPassword") val newPassword: String
)

data class ChangePasswordRequest(
    @SerializedName("oldPassword") val oldPassword: String,
    @SerializedName("newPassword") val newPassword: String
)

data class DeleteAccountRequest(
    @SerializedName("password") val password: String
)

data class AuthResponseDto(
    @SerializedName("token") val token: String,
    @SerializedName("isNew") val isNew: Boolean? = false,
    @SerializedName("user") val user: UserDto
)

data class UserSettingsDto(
    @SerializedName("lastSeenPrivacy") val lastSeenPrivacy: String? = "everyone",
    @SerializedName("profilePhotoPrivacy") val profilePhotoPrivacy: String? = "everyone",
    @SerializedName("aboutPrivacy") val aboutPrivacy: String? = "everyone",
    @SerializedName("readReceipts") val readReceipts: Boolean? = true,
    @SerializedName("shareBattery") val shareBattery: Boolean? = true
) {
    fun toDomain(): com.oma.chat.domain.model.UserPrivacySettings {
        return com.oma.chat.domain.model.UserPrivacySettings(
            lastSeenPrivacy = lastSeenPrivacy ?: "everyone",
            profilePhotoPrivacy = profilePhotoPrivacy ?: "everyone",
            aboutPrivacy = aboutPrivacy ?: "everyone",
            readReceipts = readReceipts ?: true,
            shareBattery = shareBattery ?: true
        )
    }
}

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("name") val name: String,
    @SerializedName("avatar") val avatar: String,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("lastSeen") val lastSeen: Any? = null,
    @SerializedName("battery") val battery: Int? = null,
    @SerializedName("settings") val settings: UserSettingsDto? = null,
    @SerializedName("blockedUsers") val blockedUsers: List<String>? = null
) {
    fun toDomain(): User {
        val parsedLastSeen = when (lastSeen) {
            is Number -> lastSeen.toLong()
            is String -> lastSeen.toLongOrNull() ?: 0L
            else -> 0L
        }
        return User(
            id = id,
            username = username,
            name = name,
            avatar = avatar,
            bio = bio,
            phone = phone,
            lastSeen = parsedLastSeen,
            battery = battery,
            settings = settings?.toDomain() ?: com.oma.chat.domain.model.UserPrivacySettings(),
            blockedUsers = blockedUsers ?: emptyList()
        )
    }
}

data class SimpleMessageResponseDto(
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)
