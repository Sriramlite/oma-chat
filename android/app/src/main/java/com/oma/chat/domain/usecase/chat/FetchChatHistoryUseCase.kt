package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.model.Message
import com.oma.chat.domain.repository.ChatRepository
import javax.inject.Inject

class FetchChatHistoryUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        ownerUserId: String,
        chatId: String,
        since: Long? = null
    ): Result<List<Message>> {
        return chatRepository.fetchChatHistory(ownerUserId, chatId, since)
    }
}
