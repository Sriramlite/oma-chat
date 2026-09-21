package com.oma.chat.domain.usecase.auth

import com.oma.chat.domain.model.AuthSession
import com.oma.chat.domain.repository.AuthRepository
import javax.inject.Inject

class GoogleAuthUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<AuthSession> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID Token is required"))
        }
        return authRepository.googleAuth(idToken)
    }
}
