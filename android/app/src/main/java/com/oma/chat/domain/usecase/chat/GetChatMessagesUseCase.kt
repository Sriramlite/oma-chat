package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.model.Message
import com.oma.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChatMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(ownerUserId: String, chatId: String): Flow<List<Message>> {
        return chatRepository.getMessages(ownerUserId, chatId)
    }
}
