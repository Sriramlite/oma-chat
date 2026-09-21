package com.oma.chat.domain.usecase.auth

import com.oma.chat.domain.repository.AuthRepository
import javax.inject.Inject

class ForgotPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String): Result<String> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username is required"))
        }
        return authRepository.forgotPassword(username)
    }
}
