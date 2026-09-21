package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.repository.ChatRepository
import javax.inject.Inject

class MessageActionUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend fun editMessage(ownerUserId: String, messageId: String, newContent: String): Result<Unit> {
        if (newContent.isBlank()) {
            return Result.failure(IllegalArgumentException("Message content cannot be blank"))
        }
        return chatRepository.editMessage(ownerUserId, messageId, newContent.trim())
    }

    suspend fun deleteMessage(ownerUserId: String, messageId: String, mode: String = "everyone"): Result<Unit> {
        return chatRepository.deleteMessage(ownerUserId, messageId, mode)
    }

    suspend fun starMessage(ownerUserId: String, messageId: String, isStarred: Boolean): Result<Unit> {
        return chatRepository.starMessage(ownerUserId, messageId, isStarred)
    }

    suspend fun pinMessage(ownerUserId: String, messageId: String, isPinned: Boolean): Result<Unit> {
        return chatRepository.pinMessage(ownerUserId, messageId, isPinned)
    }
}
