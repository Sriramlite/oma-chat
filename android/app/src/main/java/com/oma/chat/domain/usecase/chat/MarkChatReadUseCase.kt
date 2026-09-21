package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.repository.ChatRepository
import javax.inject.Inject

class MarkChatReadUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(ownerUserId: String, chatId: String): Result<Unit> {
        return chatRepository.markChatAsRead(ownerUserId, chatId)
    }
}
