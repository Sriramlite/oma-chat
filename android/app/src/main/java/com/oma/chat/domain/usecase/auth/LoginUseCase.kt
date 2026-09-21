package com.oma.chat.domain.usecase.auth

import com.oma.chat.domain.model.AuthSession
import com.oma.chat.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String): Result<AuthSession> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username is required"))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password is required"))
        }
        return authRepository.login(username, password)
    }
}
