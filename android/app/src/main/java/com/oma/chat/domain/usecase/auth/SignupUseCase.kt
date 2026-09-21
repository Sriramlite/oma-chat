package com.oma.chat.domain.usecase.auth

import com.oma.chat.domain.model.AuthSession
import com.oma.chat.domain.repository.AuthRepository
import javax.inject.Inject

class SignupUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        name: String,
        phone: String
    ): Result<AuthSession> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username is required"))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters long"))
        }
        val cleanPhone = phone.replace(Regex("\\D"), "")
        if (cleanPhone.length < 10) {
            return Result.failure(IllegalArgumentException("Phone number must have at least 10 digits"))
        }
        return authRepository.signup(
            username = username,
            password = password,
            name = if (name.isNotBlank()) name else username,
            phone = cleanPhone
        )
    }
}
