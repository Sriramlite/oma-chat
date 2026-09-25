package com.oma.chat.presentation.settings.privacy

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.domain.model.User
import com.oma.chat.domain.model.UserPrivacySettings
import com.oma.chat.domain.usecase.user.BlockUserUseCase
import com.oma.chat.domain.usecase.user.GetBlockedUsersUseCase
import com.oma.chat.domain.usecase.user.GetMeUseCase
import com.oma.chat.domain.usecase.user.UpdatePrivacyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val lastSeenPrivacy: String = "everyone", // "everyone", "contacts", "nobody"
    val profilePhotoPrivacy: String = "everyone",
    val aboutPrivacy: String = "everyone",
    val phonePrivacy: String = "everyone",
    val readReceipts: Boolean = true,
    val shareBattery: Boolean = true,
    val blockedUsersCount: Int = 0,
    val blockedUsersList: List<User> = emptyList(),
    val isLoadingBlockedUsers: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

sealed interface PrivacyEvent {
    data class ShowSnackbar(val message: String) : PrivacyEvent
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val getMeUseCase: GetMeUseCase,
    private val updatePrivacyUseCase: UpdatePrivacyUseCase,
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase,
    private val blockUserUseCase: BlockUserUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<PrivacyEvent>()
    val eventFlow: SharedFlow<PrivacyEvent> = _eventFlow.asSharedFlow()

    init {
        observeCurrentUser()
        refresh()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            getMeUseCase.asFlow().collectLatest { user ->
                if (user != null) {
                    val s = user.settings
                    _uiState.update { current ->
                        current.copy(
                            lastSeenPrivacy = s.lastSeenPrivacy,
                            profilePhotoPrivacy = s.profilePhotoPrivacy,
                            aboutPrivacy = s.aboutPrivacy,
                            phonePrivacy = s.phonePrivacy,
                            readReceipts = s.readReceipts,
                            shareBattery = s.shareBattery,
                            blockedUsersCount = user.blockedUsers.size
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            getMeUseCase()
            loadBlockedUsers()
        }
    }

    fun updateLastSeen(value: String) {
        val updated = currentSettings().copy(lastSeenPrivacy = value)
        savePrivacySettings(updated)
    }

    fun updateProfilePhoto(value: String) {
        val updated = currentSettings().copy(profilePhotoPrivacy = value)
        savePrivacySettings(updated)
    }

    fun updateAbout(value: String) {
        val updated = currentSettings().copy(aboutPrivacy = value)
        savePrivacySettings(updated)
    }

    fun updatePhone(value: String) {
        val updated = currentSettings().copy(phonePrivacy = value)
        savePrivacySettings(updated)
    }

    fun toggleReadReceipts(enabled: Boolean) {
        val updated = currentSettings().copy(readReceipts = enabled)
        savePrivacySettings(updated)
    }

    fun toggleShareBattery(enabled: Boolean) {
        val updated = currentSettings().copy(shareBattery = enabled)
        savePrivacySettings(updated)
    }

    private fun currentSettings(): UserPrivacySettings {
        val s = _uiState.value
        return UserPrivacySettings(
            lastSeenPrivacy = s.lastSeenPrivacy,
            profilePhotoPrivacy = s.profilePhotoPrivacy,
            aboutPrivacy = s.aboutPrivacy,
            phonePrivacy = s.phonePrivacy,
            readReceipts = s.readReceipts,
            shareBattery = s.shareBattery
        )
    }

    private fun savePrivacySettings(settings: UserPrivacySettings) {
        // Optimistic UI update
        _uiState.update {
            it.copy(
                lastSeenPrivacy = settings.lastSeenPrivacy,
                profilePhotoPrivacy = settings.profilePhotoPrivacy,
                aboutPrivacy = settings.aboutPrivacy,
                phonePrivacy = settings.phonePrivacy,
                readReceipts = settings.readReceipts,
                shareBattery = settings.shareBattery
            )
        }

        viewModelScope.launch {
            val result = updatePrivacyUseCase(settings)
            result.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage) }
                _eventFlow.emit(PrivacyEvent.ShowSnackbar(err.localizedMessage ?: "Failed to save privacy settings"))
            }
        }
    }

    fun loadBlockedUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBlockedUsers = true) }
            val result = getBlockedUsersUseCase()
            result.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        blockedUsersList = list,
                        blockedUsersCount = list.size,
                        isLoadingBlockedUsers = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingBlockedUsers = false) }
            }
        }
    }

    fun unblockUser(userId: String, userName: String) {
        viewModelScope.launch {
            val result = blockUserUseCase(userId, "unblock")
            result.onSuccess {
                _uiState.update { current ->
                    val newList = current.blockedUsersList.filter { it.id != userId }
                    current.copy(
                        blockedUsersList = newList,
                        blockedUsersCount = newList.size
                    )
                }
                _eventFlow.emit(PrivacyEvent.ShowSnackbar("Unblocked $userName"))
                refresh()
            }.onFailure { err ->
                _eventFlow.emit(PrivacyEvent.ShowSnackbar(err.localizedMessage ?: "Failed to unblock user"))
            }
        }
    }
}
