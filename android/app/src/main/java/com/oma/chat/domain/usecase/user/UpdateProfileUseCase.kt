package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.UserRepository
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(name: String?, bio: String?, avatar: String?): Result<User> {
        return userRepository.updateProfile(name, bio, avatar)
    }
}
