package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.repository.UserRepository
import javax.inject.Inject

class ChangePasswordUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): Result<String> {
        return userRepository.changePassword(oldPassword, newPassword)
    }
}
