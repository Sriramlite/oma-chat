package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.repository.UserRepository
import javax.inject.Inject

class BlockUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, action: String): Result<String> {
        return userRepository.blockUser(userId, action)
    }
}
