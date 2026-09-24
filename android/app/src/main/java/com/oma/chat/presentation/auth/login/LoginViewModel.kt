package com.oma.chat.presentation.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.domain.usecase.auth.GoogleAuthUseCase
import com.oma.chat.domain.usecase.auth.LoginUseCase
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

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface LoginEvent {
    object LoginSuccess : LoginEvent
    data class ShowSnackbar(val message: String) : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val googleAuthUseCase: GoogleAuthUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<LoginEvent>()
    val eventFlow: SharedFlow<LoginEvent> = _eventFlow.asSharedFlow()

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun login() {
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password

        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your username") }
            return
        }
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = loginUseCase(username, password)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    _eventFlow.emit(LoginEvent.LoginSuccess)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Login failed. Please check your credentials."
                        )
                    }
                }
            )
        }
    }

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleLoading = true, errorMessage = null) }
            val result = googleAuthUseCase(idToken)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isGoogleLoading = false) }
                    _eventFlow.emit(LoginEvent.LoginSuccess)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = error.localizedMessage ?: "Google Sign-In verification failed."
                        )
                    }
                }
            )
        }
    }
}
