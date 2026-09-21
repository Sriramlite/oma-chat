package com.oma.chat.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.domain.model.User
import com.oma.chat.domain.usecase.user.ChangePasswordUseCase
import com.oma.chat.domain.usecase.user.DeleteAccountUseCase
import com.oma.chat.domain.usecase.user.GetMeUseCase
import com.oma.chat.domain.usecase.user.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class ProfileUiState(
    val user: User? = null,
    val name: String = "",
    val bio: String = "",
    val avatar: String = "",
    val isUpdating: Boolean = false,
    val showChangePasswordDialog: Boolean = false,
    val showDeleteAccountDialog: Boolean = false,
    val oldPassword: String = "",
    val newPassword: String = "",
    val deletePassword: String = "",
    val isChangingPassword: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

sealed interface ProfileEvent {
    object LoggedOut : ProfileEvent
    data class ShowSnackbar(val message: String) : ProfileEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getMeUseCase: GetMeUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val authPreferences: AuthPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<ProfileEvent>()
    val eventFlow: SharedFlow<ProfileEvent> = _eventFlow.asSharedFlow()

    init {
        observeUser()
        refreshProfile()
    }

    private fun observeUser() {
        viewModelScope.launch {
            getMeUseCase.asFlow().collectLatest { user ->
                if (user != null) {
                    _uiState.update {
                        it.copy(
                            user = user,
                            name = user.name,
                            bio = user.bio ?: "",
                            avatar = user.avatar
                        )
                    }
                }
            }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            getMeUseCase()
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onBioChanged(bio: String) {
        _uiState.update { it.copy(bio = bio) }
    }

    fun onAvatarChanged(avatar: String) {
        _uiState.update { it.copy(avatar = avatar) }
    }

    fun onOldPasswordChanged(password: String) {
        _uiState.update { it.copy(oldPassword = password) }
    }

    fun onNewPasswordChanged(password: String) {
        _uiState.update { it.copy(newPassword = password) }
    }

    fun onDeletePasswordChanged(password: String) {
        _uiState.update { it.copy(deletePassword = password) }
    }

    fun toggleChangePasswordDialog(show: Boolean) {
        _uiState.update {
            it.copy(
                showChangePasswordDialog = show,
                oldPassword = "",
                newPassword = ""
            )
        }
    }

    fun toggleDeleteAccountDialog(show: Boolean) {
        _uiState.update {
            it.copy(
                showDeleteAccountDialog = show,
                deletePassword = ""
            )
        }
    }

    fun uploadAvatar(base64Avatar: String) {
        _uiState.update { it.copy(avatar = base64Avatar) }
        updateProfile()
    }

    fun updateProfile() {
        val name = _uiState.value.name.trim()
        val bio = _uiState.value.bio.trim()
        val avatar = _uiState.value.avatar.trim()

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, errorMessage = null) }
            val result = updateProfileUseCase(name, bio, avatar)
            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            user = user,
                            successMessage = "Profile updated successfully"
                        )
                    }
                    _eventFlow.emit(ProfileEvent.ShowSnackbar("Profile updated successfully"))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            errorMessage = error.localizedMessage ?: "Failed to update profile"
                        )
                    }
                }
            )
        }
    }

    fun changePassword() {
        val old = _uiState.value.oldPassword
        val new = _uiState.value.newPassword

        if (old.isBlank() || new.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter both old and new password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, errorMessage = null) }
            val result = changePasswordUseCase(old, new)
            result.fold(
                onSuccess = { msg ->
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            showChangePasswordDialog = false,
                            oldPassword = "",
                            newPassword = ""
                        )
                    }
                    _eventFlow.emit(ProfileEvent.ShowSnackbar(msg))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            errorMessage = error.localizedMessage ?: "Failed to change password"
                        )
                    }
                }
            )
        }
    }

    fun deleteAccount() {
        val password = _uiState.value.deletePassword
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Password is required to delete account") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true, errorMessage = null) }
            val result = deleteAccountUseCase(password)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isDeletingAccount = false, showDeleteAccountDialog = false) }
                    _eventFlow.emit(ProfileEvent.LoggedOut)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDeletingAccount = false,
                            errorMessage = error.localizedMessage ?: "Failed to delete account"
                        )
                    }
                }
            )
        }
    }

    fun logout() {
        authPreferences.clear()
        viewModelScope.launch {
            _eventFlow.emit(ProfileEvent.LoggedOut)
        }
    }
}
