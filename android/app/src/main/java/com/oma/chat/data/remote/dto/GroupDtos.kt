package com.oma.chat.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.oma.chat.domain.model.Group

data class CreateGroupRequest(
    @SerializedName("name") val name: String,
    @SerializedName("members") val members: List<String>
)

data class ManageGroupRequest(
    @SerializedName("groupId") val groupId: String,
    @SerializedName("memberId") val memberId: String? = null,
    @SerializedName("action") val action: String // "leave" | "add" | "remove" | "promote" | "demote"
)

data class GroupDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("adminIds") val adminIds: List<String>? = null,
    @SerializedName("adminId") val adminId: String? = null,
    @SerializedName("members") val members: List<String>? = null,
    @SerializedName("created") val created: Long? = 0L,
    @SerializedName("lastMsg") val lastMsg: String? = null,
    @SerializedName("lastTimestamp") val lastTimestamp: Long? = null
) {
    fun toDomain(): Group {
        val effectiveAdmins = adminIds ?: (if (!adminId.isNullOrBlank()) listOf(adminId) else emptyList())
        return Group(
            id = id,
            name = name,
            avatar = avatar ?: "https://ui-avatars.com/api/?name=${name}&background=random",
            adminIds = effectiveAdmins,
            members = members ?: emptyList(),
            created = created ?: 0L,
            lastMsg = lastMsg,
            lastTimestamp = lastTimestamp
        )
    }
}

data class MessageActionRequest(
    @SerializedName("action") val action: String, // "delete" | "edit" | "star" | "pin"
    @SerializedName("messageId") val messageId: String,
    @SerializedName("newContent") val newContent: String? = null,
    @SerializedName("mode") val mode: String? = null // "everyone" | "me"
)

data class DeleteChatRequest(
    @SerializedName("chatId") val chatId: String
)
