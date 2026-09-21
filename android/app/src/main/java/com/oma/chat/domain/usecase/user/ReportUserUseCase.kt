package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.repository.UserRepository
import javax.inject.Inject

class ReportUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(targetedUserId: String, reason: String): Result<String> {
        return userRepository.reportUser(targetedUserId, reason)
    }
}
