package com.oma.chat.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.local.dao.UserDao
import com.oma.chat.data.local.entity.UserEntity
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.api.BatchUsersRequest
import com.oma.chat.data.remote.api.UserApi
import com.oma.chat.data.remote.socket.SocketManager
import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.UserRepository
import com.oma.chat.domain.usecase.chat.DeleteChatUseCase
import com.oma.chat.domain.usecase.chat.ObservePresenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val isOnline: Boolean = false,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val isMe: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

sealed interface UserProfileEvent {
    data class ShowMessage(val message: String) : UserProfileEvent
    object ChatDeleted : UserProfileEvent
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authPreferences: AuthPreferences,
    private val userDao: UserDao,
    private val userApi: UserApi,
    private val userRepository: UserRepository,
    private val socketManager: SocketManager,
    private val observePresenceUseCase: ObservePresenceUseCase,
    private val deleteChatUseCase: DeleteChatUseCase,
    private val getMeUseCase: com.oma.chat.domain.usecase.user.GetMeUseCase
) : ViewModel() {

    val userId: String = checkNotNull(savedStateHandle["userId"])
    private val myUserId: String = authPreferences.getUserId() ?: ""

    private val _uiState = MutableStateFlow(
        UserProfileUiState(
            isMe = userId == myUserId
        )
    )
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UserProfileEvent>()
    val events: SharedFlow<UserProfileEvent> = _events.asSharedFlow()

    init {
        loadUser()
        observePresence()
        observeSocketEvents()
    }

    private fun loadUser() {
        // 1. Observe local Room DB
        viewModelScope.launch {
            userDao.getUserByIdFlow(userId).collect { entity ->
                if (entity != null) {
                    val isSelf = userId == myUserId
                    val prefUser = if (isSelf) authPreferences.getUser() else null
                    val isBlocked = entity.isBlocked ||
                            authPreferences.getUser()?.blockedUsers?.contains(userId) == true
                    _uiState.update { current ->
                        current.copy(
                            user = User(
                                id = entity.id,
                                username = entity.username,
                                name = entity.name,
                                avatar = entity.avatar,
                                bio = entity.bio,
                                lastSeen = entity.lastSeen,
                                phone = if (isSelf) prefUser?.phone else (current.user?.phone ?: prefUser?.phone),
                                isBlocked = isBlocked,
                                battery = entity.batteryLevel,
                                isCharging = entity.isCharging
                            ),
                            isBlocked = isBlocked,
                            isLoading = false
                        )
                    }
                }
            }
        }

        // 2. Refresh from backend batch API or GetMe
        viewModelScope.launch {
            try {
                if (userId == myUserId) {
                    val me = getMeUseCase().getOrNull() ?: authPreferences.getUser()
                    if (me != null) {
                        _uiState.update { it.copy(user = me, isLoading = false) }
                    }
                } else {
                    val resp = userApi.batchGetUsers(BatchUsersRequest(listOf(userId)))
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
                                isBlocked = u.isBlocked,
                                batteryLevel = u.battery,
                                isCharging = u.isCharging
                            )
                        )
                        _uiState.update { current ->
                            current.copy(
                                user = u,
                                isBlocked = u.isBlocked || authPreferences.getUser()?.blockedUsers?.contains(userId) == true,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun observePresence() {
        viewModelScope.launch {
            observePresenceUseCase().collect { onlineSet ->
                _uiState.update { it.copy(isOnline = onlineSet.contains(userId)) }
            }
        }
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            socketManager.userStatusEvents.collect { event ->
                if (event.userId == userId) {
                    _uiState.update { current ->
                        val currentUser = current.user
                        current.copy(
                            isOnline = event.online,
                            user = currentUser?.copy(
                                lastSeen = if (event.lastSeen > 0L) event.lastSeen else currentUser.lastSeen,
                                battery = event.batteryLevel ?: currentUser.battery,
                                isCharging = if (event.batteryLevel != null) event.isCharging else currentUser.isCharging
                            )
                        )
                    }
                }
            }
        }
    }

    fun toggleBlockUser() {
        val currentlyBlocked = _uiState.value.isBlocked
        val action = if (currentlyBlocked) "unblock" else "block"

        viewModelScope.launch {
            val result = userRepository.blockUser(userId, action)
            if (result.isSuccess) {
                val newBlocked = !currentlyBlocked
                _uiState.update { it.copy(isBlocked = newBlocked) }
                userDao.updateBlockedStatus(userId, newBlocked)
                _events.emit(UserProfileEvent.ShowMessage("User ${if (newBlocked) "blocked" else "unblocked"} successfully"))
            } else {
                _events.emit(UserProfileEvent.ShowMessage(result.exceptionOrNull()?.message ?: "Action failed"))
            }
        }
    }

    fun reportUser(reason: String) {
        viewModelScope.launch {
            val result = userRepository.reportUser(userId, reason)
            if (result.isSuccess) {
                _events.emit(UserProfileEvent.ShowMessage("Report submitted successfully"))
            } else {
                _events.emit(UserProfileEvent.ShowMessage(result.exceptionOrNull()?.message ?: "Failed to submit report"))
            }
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            deleteChatUseCase(myUserId, userId)
            _events.emit(UserProfileEvent.ChatDeleted)
        }
    }

    fun toggleMuteNotifications() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }
}
