package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.model.Conversation
import com.oma.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecentConversationsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(ownerUserId: String): Flow<List<Conversation>> {
        return chatRepository.getRecentConversations(ownerUserId)
    }
}
