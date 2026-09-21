package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.ChatRepository
import javax.inject.Inject

class SearchUsersUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(query: String): Result<List<User>> {
        return chatRepository.searchUsers(query)
    }
}
