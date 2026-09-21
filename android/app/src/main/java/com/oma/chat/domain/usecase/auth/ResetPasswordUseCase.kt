package com.oma.chat.domain.usecase.auth

import com.oma.chat.domain.repository.AuthRepository
import javax.inject.Inject

class ResetPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        otp: String,
        newPassword: String
    ): Result<String> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username is required"))
        }
        if (otp.isBlank()) {
            return Result.failure(IllegalArgumentException("OTP is required"))
        }
        if (newPassword.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters long"))
        }
        return authRepository.resetPassword(username, otp, newPassword)
    }
}
