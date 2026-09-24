package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.model.User
import com.oma.chat.domain.model.UserPrivacySettings
import com.oma.chat.domain.repository.UserRepository
import javax.inject.Inject

class UpdatePrivacyUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(settings: UserPrivacySettings): Result<User> {
        return userRepository.updatePrivacySettings(settings)
    }
}
