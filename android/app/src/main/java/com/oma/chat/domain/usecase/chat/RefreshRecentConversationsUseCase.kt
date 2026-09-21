package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.repository.ChatRepository
import javax.inject.Inject

class RefreshRecentConversationsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(ownerUserId: String): Result<Unit> {
        return chatRepository.refreshRecentConversations(ownerUserId)
    }
}
