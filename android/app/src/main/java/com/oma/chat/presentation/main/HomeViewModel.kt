package com.oma.chat.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.domain.model.User
import com.oma.chat.domain.usecase.auth.GetCurrentUserUseCase
import com.oma.chat.domain.usecase.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMeUseCase: com.oma.chat.domain.usecase.user.GetMeUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    val currentUser: StateFlow<User?> = getMeUseCase.asFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            getMeUseCase()
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}
