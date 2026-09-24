package com.oma.chat.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.domain.model.Message
import com.oma.chat.domain.usecase.chat.DeleteChatUseCase
import com.oma.chat.domain.usecase.chat.FetchChatHistoryUseCase
import com.oma.chat.domain.usecase.chat.GetChatMessagesUseCase
import com.oma.chat.domain.usecase.chat.MarkChatReadUseCase
import com.oma.chat.domain.usecase.chat.MessageActionUseCase
import com.oma.chat.domain.usecase.chat.ObservePresenceUseCase
import com.oma.chat.domain.usecase.chat.SendMessageUseCase
import com.oma.chat.domain.usecase.chat.SendTypingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.oma.chat.data.local.dao.UserDao
import com.oma.chat.data.local.entity.UserEntity
import com.oma.chat.data.remote.api.BatchUsersRequest
import com.oma.chat.data.remote.api.UserApi
import com.oma.chat.data.remote.socket.SocketManager

data class ChatUiState(
    val chatId: String = "",
    val chatName: String = "",
    val partnerAvatar: String = "",
    val isPartnerOnline: Boolean = false,
    val isPartnerTyping: Boolean = false,
    val partnerLastSeen: Long = 0L,
    val partnerBatteryLevel: Int? = null,
    val isPartnerCharging: Boolean = false,
    val messageInput: String = "",
    val messages: List<Message> = emptyList(),
    val selectedMessage: Message? = null,
    val isEditing: Boolean = false,
    val editingText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authPreferences: AuthPreferences,
    private val getChatMessagesUseCase: GetChatMessagesUseCase,
    private val fetchChatHistoryUseCase: FetchChatHistoryUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val markChatReadUseCase: MarkChatReadUseCase,
    private val sendTypingUseCase: SendTypingUseCase,
    private val observePresenceUseCase: ObservePresenceUseCase,
    private val messageActionUseCase: MessageActionUseCase,
    private val deleteChatUseCase: DeleteChatUseCase,
    private val userDao: UserDao,
    private val userApi: UserApi,
    private val socketManager: SocketManager
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val chatName: String = checkNotNull(savedStateHandle["chatName"])
    val ownerUserId: String = authPreferences.getUserId() ?: ""

    private val _uiState = MutableStateFlow(
        ChatUiState(
            chatId = chatId,
            chatName = chatName
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var typingJob: Job? = null

    init {
        // 1. Observe local messages from Room
        viewModelScope.launch {
            getChatMessagesUseCase(ownerUserId, chatId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }

        // 2. Observe presence for this partner
        viewModelScope.launch {
            observePresenceUseCase().collect { onlineSet ->
                _uiState.update { it.copy(isPartnerOnline = onlineSet.contains(chatId)) }
            }
        }

        // 3. Observe typing events from partner
        viewModelScope.launch {
            sendTypingUseCase.observeTyping().collect { event ->
                if (event.senderId == chatId) {
                    _uiState.update { it.copy(isPartnerTyping = event.isTyping) }
                }
            }
        }

        // 4. Observe partner user from Room (avatar, lastSeen, battery)
        viewModelScope.launch {
            userDao.getUserByIdFlow(chatId).collect { partner ->
                if (partner != null) {
                    _uiState.update { current ->
                        current.copy(
                            partnerAvatar = if (partner.avatar.isNotBlank()) partner.avatar else current.partnerAvatar,
                            partnerLastSeen = if (partner.lastSeen > 0L) partner.lastSeen else current.partnerLastSeen,
                            partnerBatteryLevel = partner.batteryLevel ?: current.partnerBatteryLevel,
                            isPartnerCharging = partner.isCharging
                        )
                    }
                }
            }
        }

        // 5. Fetch fresh partner details from backend
        viewModelScope.launch {
            try {
                val resp = userApi.batchGetUsers(BatchUsersRequest(listOf(chatId)))
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val u = resp.body()!!.first().toDomain()
                    userDao.insertUser(
                        UserEntity(
                            id = u.id,
                            username = u.username,
                            name = u.name,
                            avatar = u.avatar,
                            bio = u.bio,
                            lastSeen = u.lastSeen,
                            batteryLevel = u.battery,
                            isCharging = u.isCharging
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 6. Observe real-time status & battery updates via Socket
        viewModelScope.launch {
            socketManager.userStatusEvents.collect { event ->
                if (event.userId == chatId) {
                    _uiState.update { current ->
                        current.copy(
                            isPartnerOnline = event.online,
                            partnerLastSeen = if (event.lastSeen > 0L) event.lastSeen else current.partnerLastSeen,
                            partnerBatteryLevel = event.batteryLevel ?: current.partnerBatteryLevel,
                            isPartnerCharging = if (event.batteryLevel != null) event.isCharging else current.isPartnerCharging
                        )
                    }
                    if (event.batteryLevel != null) {
                        userDao.updateBattery(chatId, event.batteryLevel, event.isCharging)
                    }
                    if (event.lastSeen > 0L) {
                        userDao.updateLastSeen(chatId, event.lastSeen)
                    }
                }
            }
        }

        // 7. Mark chat as read and fetch history
        viewModelScope.launch {
            markChatReadUseCase(ownerUserId, chatId)
            fetchChatHistoryUseCase(ownerUserId, chatId)
        }
    }

    fun onMessageInputChange(newText: String) {
        _uiState.update { it.copy(messageInput = newText) }

        // Trigger typing indicator with debounce
        if (newText.isNotBlank()) {
            sendTypingUseCase.sendTyping(chatId)
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                delay(3000)
                sendTypingUseCase.sendStopTyping(chatId)
            }
        } else {
            typingJob?.cancel()
            sendTypingUseCase.sendStopTyping(chatId)
        }
    }

    fun sendMessage() {
        val text = _uiState.value.messageInput.trim()
        if (text.isBlank()) return

        // Clear input immediately for responsive typing experience
        _uiState.update { it.copy(messageInput = "") }
        typingJob?.cancel()
        sendTypingUseCase.sendStopTyping(chatId)

        viewModelScope.launch {
            sendMessageUseCase(
                ownerUserId = ownerUserId,
                receiverId = chatId,
                content = text,
                type = "text"
            )
        }
    }

    fun selectMessage(message: Message?) {
        _uiState.update { it.copy(selectedMessage = message) }
    }

    fun startEditingMessage(message: Message) {
        _uiState.update {
            it.copy(
                selectedMessage = null,
                isEditing = true,
                editingText = message.content
            )
        }
    }

    fun onEditingTextChanged(text: String) {
        _uiState.update { it.copy(editingText = text) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, editingText = "") }
    }

    fun confirmEditMessage(messageId: String) {
        val newContent = _uiState.value.editingText.trim()
        if (newContent.isBlank()) return

        _uiState.update { it.copy(isEditing = false, editingText = "") }
        viewModelScope.launch {
            messageActionUseCase.editMessage(ownerUserId, messageId, newContent)
        }
    }

    fun deleteMessage(messageId: String, mode: String = "everyone") {
        _uiState.update { it.copy(selectedMessage = null) }
        viewModelScope.launch {
            messageActionUseCase.deleteMessage(ownerUserId, messageId, mode)
        }
    }

    fun toggleStar(messageId: String, currentStarred: Boolean) {
        _uiState.update { it.copy(selectedMessage = null) }
        viewModelScope.launch {
            messageActionUseCase.starMessage(ownerUserId, messageId, !currentStarred)
        }
    }

    fun togglePin(messageId: String, currentPinned: Boolean) {
        _uiState.update { it.copy(selectedMessage = null) }
        viewModelScope.launch {
            messageActionUseCase.pinMessage(ownerUserId, messageId, !currentPinned)
        }
    }

    fun deleteChat(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteChatUseCase(ownerUserId, chatId)
            onDeleted()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sendTypingUseCase.sendStopTyping(chatId)
    }
}
