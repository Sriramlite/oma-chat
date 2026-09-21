package com.oma.chat.domain.usecase.chat

import com.oma.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePresenceUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<Set<String>> {
        return chatRepository.observeOnlineUsers()
    }

    fun connect() {
        chatRepository.connectSocket()
    }

    fun disconnect() {
        chatRepository.disconnectSocket()
    }
}
