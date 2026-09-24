package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.UserRepository
import javax.inject.Inject

class GetBlockedUsersUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): Result<List<User>> {
        return userRepository.getBlockedUsers()
    }
}
