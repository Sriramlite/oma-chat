package com.oma.chat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.domain.model.User
import com.oma.chat.domain.usecase.auth.LogoutUseCase
import com.oma.chat.domain.usecase.user.BlockUserUseCase
import com.oma.chat.domain.usecase.user.ChangePasswordUseCase
import com.oma.chat.domain.usecase.user.DeleteAccountUseCase
import com.oma.chat.domain.usecase.user.GetMeUseCase
import com.oma.chat.domain.usecase.user.ReportUserUseCase
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

enum class AppThemeSetting {
    SYSTEM, LIGHT, DARK
}

data class SettingsUiState(
    val user: User? = null,
    val selectedTheme: AppThemeSetting = AppThemeSetting.SYSTEM,
    val isNotificationsEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isReadReceiptsEnabled: Boolean = true,
    val isHighPriorityNotifications: Boolean = true,
    val isAutoDownloadWifi: Boolean = true,
    val isAutoDownloadCellular: Boolean = false,
    
    // Dialog states
    val showThemeDialog: Boolean = false,
    val showLogoutConfirmDialog: Boolean = false,
    val showChangePasswordDialog: Boolean = false,
    val showDeleteAccountDialog: Boolean = false,
    val showReportIssueDialog: Boolean = false,
    val showClearChatsDialog: Boolean = false,
    
    // Form fields
    val oldPassword: String = "",
    val newPassword: String = "",
    val deletePassword: String = "",
    val reportReason: String = "",
    
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

sealed interface SettingsEvent {
    object LoggedOut : SettingsEvent
    data class ShowSnackbar(val text: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getMeUseCase: GetMeUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val reportUserUseCase: ReportUserUseCase,
    private val authPreferences: AuthPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<SettingsEvent>()
    val eventFlow: SharedFlow<SettingsEvent> = _eventFlow.asSharedFlow()

    init {
        observeUser()
        refreshProfile()
    }

    private fun observeUser() {
        viewModelScope.launch {
            getMeUseCase.asFlow().collectLatest { user ->
                if (user != null) {
                    _uiState.update { it.copy(user = user) }
                }
            }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            getMeUseCase()
        }
    }

    fun setTheme(theme: AppThemeSetting) {
        _uiState.update { it.copy(selectedTheme = theme, showThemeDialog = false) }
    }

    fun toggleNotifications(enabled: Boolean) {
        _uiState.update { it.copy(isNotificationsEnabled = enabled) }
    }

    fun toggleVibration(enabled: Boolean) {
        _uiState.update { it.copy(isVibrationEnabled = enabled) }
    }

    fun toggleReadReceipts(enabled: Boolean) {
        _uiState.update { it.copy(isReadReceiptsEnabled = enabled) }
    }

    fun toggleHighPriorityNotifications(enabled: Boolean) {
        _uiState.update { it.copy(isHighPriorityNotifications = enabled) }
    }

    // Dialog state handlers
    fun setShowThemeDialog(show: Boolean) = _uiState.update { it.copy(showThemeDialog = show) }
    fun setShowLogoutConfirmDialog(show: Boolean) = _uiState.update { it.copy(showLogoutConfirmDialog = show) }
    fun setShowChangePasswordDialog(show: Boolean) = _uiState.update { it.copy(showChangePasswordDialog = show, oldPassword = "", newPassword = "") }
    fun setShowDeleteAccountDialog(show: Boolean) = _uiState.update { it.copy(showDeleteAccountDialog = show, deletePassword = "") }
    fun setShowReportIssueDialog(show: Boolean) = _uiState.update { it.copy(showReportIssueDialog = show, reportReason = "") }
    fun setShowClearChatsDialog(show: Boolean) = _uiState.update { it.copy(showClearChatsDialog = show) }

    fun onOldPasswordChanged(text: String) = _uiState.update { it.copy(oldPassword = text) }
    fun onNewPasswordChanged(text: String) = _uiState.update { it.copy(newPassword = text) }
    fun onDeletePasswordChanged(text: String) = _uiState.update { it.copy(deletePassword = text) }
    fun onReportReasonChanged(text: String) = _uiState.update { it.copy(reportReason = text) }

    fun submitChangePassword() {
        val old = _uiState.value.oldPassword.trim()
        val new = _uiState.value.newPassword.trim()
        if (old.isBlank() || new.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all password fields") }
            return
        }
        if (new.length < 6) {
            _uiState.update { it.copy(error = "New password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = changePasswordUseCase(old, new)
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess { msg ->
                _uiState.update {
                    it.copy(
                        showChangePasswordDialog = false,
                        oldPassword = "",
                        newPassword = ""
                    )
                }
                _eventFlow.emit(SettingsEvent.ShowSnackbar(msg))
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.localizedMessage ?: "Failed to change password") }
            }
        }
    }

    fun submitDeleteAccount() {
        val password = _uiState.value.deletePassword.trim()
        if (password.isBlank()) {
            _uiState.update { it.copy(error = "Password is required to delete account") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = deleteAccountUseCase(password)
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess {
                _uiState.update { it.copy(showDeleteAccountDialog = false) }
                logoutUseCase()
                _eventFlow.emit(SettingsEvent.LoggedOut)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.localizedMessage ?: "Failed to delete account") }
            }
        }
    }

    fun submitReportIssue() {
        val reason = _uiState.value.reportReason.trim()
        if (reason.isBlank()) {
            _uiState.update { it.copy(error = "Please describe the issue") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Submit report under system tag
            val currentUserId = authPreferences.getUserId() ?: ""
            val result = reportUserUseCase(targetedUserId = currentUserId, reason = "Feedback/Issue: $reason")
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess {
                _uiState.update { it.copy(showReportIssueDialog = false, reportReason = "") }
                _eventFlow.emit(SettingsEvent.ShowSnackbar("Thank you! Your report has been submitted."))
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.localizedMessage ?: "Failed to submit report") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _eventFlow.emit(SettingsEvent.LoggedOut)
        }
    }
}
