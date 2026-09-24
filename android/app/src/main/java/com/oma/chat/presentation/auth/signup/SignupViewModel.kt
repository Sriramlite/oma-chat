package com.oma.chat.presentation.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.domain.usecase.auth.GoogleAuthUseCase
import com.oma.chat.domain.usecase.auth.SignupUseCase
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

data class SignupUiState(
    val username: String = "",
    val name: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface SignupEvent {
    object SignupSuccess : SignupEvent
    data class ShowSnackbar(val message: String) : SignupEvent
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val signupUseCase: SignupUseCase,
    private val googleAuthUseCase: GoogleAuthUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<SignupEvent>()
    val eventFlow: SharedFlow<SignupEvent> = _eventFlow.asSharedFlow()

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun onPhoneChange(phone: String) {
        _uiState.update { it.copy(phone = phone, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun signup() {
        val username = _uiState.value.username.trim()
        val name = _uiState.value.name.trim()
        val phone = _uiState.value.phone.trim()
        val password = _uiState.value.password
        val confirmPassword = _uiState.value.confirmPassword

        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a username") }
            return
        }
        if (phone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your phone number") }
            return
        }
        if (password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters long") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = signupUseCase(
                username = username,
                password = password,
                name = name,
                phone = phone
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    _eventFlow.emit(SignupEvent.SignupSuccess)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Signup failed. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun signupWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleLoading = true, errorMessage = null) }
            val result = googleAuthUseCase(idToken)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isGoogleLoading = false) }
                    _eventFlow.emit(SignupEvent.SignupSuccess)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = error.localizedMessage ?: "Google Sign-Up verification failed."
                        )
                    }
                }
            )
        }
    }
}
