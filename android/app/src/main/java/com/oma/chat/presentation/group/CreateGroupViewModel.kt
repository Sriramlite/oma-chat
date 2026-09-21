package com.oma.chat.presentation.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.domain.model.Group
import com.oma.chat.domain.model.User
import com.oma.chat.domain.usecase.chat.SearchUsersUseCase
import com.oma.chat.domain.usecase.group.CreateGroupUseCase
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

data class CreateGroupUiState(
    val groupName: String = "",
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val selectedUsers: Set<User> = emptySet(),
    val isSearching: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null
)

sealed interface CreateGroupEvent {
    data class GroupCreated(val group: Group) : CreateGroupEvent
    data class ShowSnackbar(val message: String) : CreateGroupEvent
}

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val createGroupUseCase: CreateGroupUseCase,
    private val searchUsersUseCase: SearchUsersUseCase
) : ViewModel() {

    private val ownerUserId = authPreferences.getUserId() ?: ""

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<CreateGroupEvent>()
    val eventFlow: SharedFlow<CreateGroupEvent> = _eventFlow.asSharedFlow()

    init {
        // Initial search to display suggested contacts
        searchUsers("")
    }

    fun onGroupNameChanged(name: String) {
        _uiState.update { it.copy(groupName = name, errorMessage = null) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchUsers(query)
    }

    fun toggleUserSelection(user: User) {
        _uiState.update { state ->
            val current = state.selectedUsers.toMutableSet()
            if (current.any { it.id == user.id }) {
                current.removeAll { it.id == user.id }
            } else {
                current.add(user)
            }
            state.copy(selectedUsers = current)
        }
    }

    private fun searchUsers(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val result = searchUsersUseCase(query)
            result.fold(
                onSuccess = { users ->
                    val filtered = users.filter { it.id != ownerUserId }
                    _uiState.update { it.copy(searchResults = filtered, isSearching = false) }
                },
                onFailure = {
                    _uiState.update { it.copy(isSearching = false) }
                }
            )
        }
    }

    fun createGroup() {
        val name = _uiState.value.groupName.trim()
        val members = _uiState.value.selectedUsers.map { it.id }

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a group name") }
            return
        }
        if (members.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please select at least one member") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, errorMessage = null) }
            val result = createGroupUseCase(name, members)
            result.fold(
                onSuccess = { group ->
                    _uiState.update { it.copy(isCreating = false) }
                    _eventFlow.emit(CreateGroupEvent.GroupCreated(group))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            errorMessage = error.localizedMessage ?: "Failed to create group"
                        )
                    }
                }
            )
        }
    }
}
