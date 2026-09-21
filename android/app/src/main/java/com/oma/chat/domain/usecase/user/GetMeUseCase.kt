package com.oma.chat.domain.usecase.user

import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMeUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    fun asFlow(): Flow<User?> = userRepository.getCurrentUser()

    suspend operator fun invoke(): Result<User> {
        return userRepository.fetchMe()
    }
}
