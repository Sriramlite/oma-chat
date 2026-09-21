package com.oma.chat.domain.usecase.chat

import com.oma.chat.data.remote.socket.TypingEvent
import com.oma.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SendTypingUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    fun sendTyping(receiverId: String) {
        chatRepository.sendTyping(receiverId)
    }

    fun sendStopTyping(receiverId: String) {
        chatRepository.sendStopTyping(receiverId)
    }

    fun observeTyping(): Flow<TypingEvent> {
        return chatRepository.observeTypingEvents()
    }
}
