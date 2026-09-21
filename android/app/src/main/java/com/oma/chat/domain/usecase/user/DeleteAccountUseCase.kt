package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.repository.UserRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(password: String): Result<String> {
        return userRepository.deleteAccount(password)
    }
}
