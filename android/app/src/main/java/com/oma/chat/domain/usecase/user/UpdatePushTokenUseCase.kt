package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.repository.UserRepository
import javax.inject.Inject

class UpdatePushTokenUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(token: String): Result<String> {
        return userRepository.updatePushToken(token)
    }
}
