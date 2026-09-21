package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.model.Message
import com.oma.chat.domain.repository.ChatRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        ownerUserId: String,
        receiverId: String,
        content: String,
        type: String = "text",
        replyToId: String? = null
    ): Result<Message> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Message content cannot be blank"))
        }
        return chatRepository.sendMessage(
            ownerUserId = ownerUserId,
            receiverId = receiverId,
            content = content.trim(),
            type = type,
            replyToId = replyToId
        )
    }
}
