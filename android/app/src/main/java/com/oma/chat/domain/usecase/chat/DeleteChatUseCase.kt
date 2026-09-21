package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.repository.ChatRepository
import javax.inject.Inject

class DeleteChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(ownerUserId: String, chatId: String): Result<Unit> {
        return chatRepository.deleteChat(ownerUserId, chatId)
    }
}
