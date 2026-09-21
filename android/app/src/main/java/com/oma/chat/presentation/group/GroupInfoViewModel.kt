package com.oma.chat.presentation.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.domain.model.Group
import com.oma.chat.domain.model.User
import com.oma.chat.domain.usecase.chat.SearchUsersUseCase
import com.oma.chat.domain.usecase.group.GetGroupByIdUseCase
import com.oma.chat.domain.usecase.group.GetGroupMembersUseCase
import com.oma.chat.domain.usecase.group.ManageGroupUseCase
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

data class GroupInfoUiState(
    val group: Group? = null,
    val members: List<User> = emptyList(),
    val currentUserId: String = "",
    val isAdmin: Boolean = false,
    val isLoading: Boolean = true,
    val actionInProgress: Boolean = false,
    val showAddMemberDialog: Boolean = false,
    val addMemberSearchQuery: String = "",
    val addMemberSearchResults: List<User> = emptyList(),
    val isSearchingAddMembers: Boolean = false,
    val showLeaveGroupConfirmation: Boolean = false,
    val errorMessage: String? = null
)

sealed interface GroupInfoEvent {
    object GroupLeft : GroupInfoEvent
    data class ShowSnackbar(val message: String) : GroupInfoEvent
}

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authPreferences: AuthPreferences,
    private val getGroupByIdUseCase: GetGroupByIdUseCase,
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    private val manageGroupUseCase: ManageGroupUseCase,
    private val searchUsersUseCase: SearchUsersUseCase
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val currentUserId = authPreferences.getUserId() ?: ""

    private val _uiState = MutableStateFlow(
        GroupInfoUiState(
            currentUserId = currentUserId
        )
    )
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<GroupInfoEvent>()
    val eventFlow: SharedFlow<GroupInfoEvent> = _eventFlow.asSharedFlow()

    init {
        observeGroup()
    }

    private fun observeGroup() {
        viewModelScope.launch {
            getGroupByIdUseCase(groupId).collectLatest { group ->
                if (group != null) {
                    val isAdmin = group.isAdmin(currentUserId)
                    _uiState.update {
                        it.copy(
                            group = group,
                            isAdmin = isAdmin,
                            isLoading = false
                        )
                    }
                    loadMembers(group.members)
                }
            }
        }
    }

    private fun loadMembers(memberIds: List<String>) {
        viewModelScope.launch {
            val result = getGroupMembersUseCase(memberIds)
            result.fold(
                onSuccess = { members ->
                    _uiState.update { it.copy(members = members) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.localizedMessage) }
                }
            )
        }
    }

    fun onAddMemberSearchQueryChanged(query: String) {
        _uiState.update { it.copy(addMemberSearchQuery = query) }
        searchUsersForAdd(query)
    }

    fun toggleAddMemberDialog(show: Boolean) {
        _uiState.update {
            it.copy(
                showAddMemberDialog = show,
                addMemberSearchQuery = "",
                addMemberSearchResults = emptyList()
            )
        }
        if (show) {
            searchUsersForAdd("")
        }
    }

    fun toggleLeaveConfirmation(show: Boolean) {
        _uiState.update { it.copy(showLeaveGroupConfirmation = show) }
    }

    private fun searchUsersForAdd(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingAddMembers = true) }
            val result = searchUsersUseCase(query)
            result.fold(
                onSuccess = { users ->
                    val existingMemberIds = _uiState.value.group?.members ?: emptyList()
                    val nonMembers = users.filter { it.id !in existingMemberIds && it.id != currentUserId }
                    _uiState.update {
                        it.copy(
                            addMemberSearchResults = nonMembers,
                            isSearchingAddMembers = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isSearchingAddMembers = false) }
                }
            )
        }
    }

    fun addMember(memberId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true) }
            val result = manageGroupUseCase.addMember(groupId, memberId)
            result.fold(
                onSuccess = { msg ->
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            showAddMemberDialog = false
                        )
                    }
                    _eventFlow.emit(GroupInfoEvent.ShowSnackbar(msg))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = error.localizedMessage ?: "Failed to add member"
                        )
                    }
                }
            )
        }
    }

    fun removeMember(memberId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true) }
            val result = manageGroupUseCase.removeMember(groupId, memberId)
            result.fold(
                onSuccess = { msg ->
                    _uiState.update { it.copy(actionInProgress = false) }
                    _eventFlow.emit(GroupInfoEvent.ShowSnackbar(msg))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = error.localizedMessage ?: "Failed to remove member"
                        )
                    }
                }
            )
        }
    }

    fun promoteAdmin(memberId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true) }
            val result = manageGroupUseCase.promoteAdmin(groupId, memberId)
            result.fold(
                onSuccess = { msg ->
                    _uiState.update { it.copy(actionInProgress = false) }
                    _eventFlow.emit(GroupInfoEvent.ShowSnackbar(msg))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = error.localizedMessage ?: "Failed to promote admin"
                        )
                    }
                }
            )
        }
    }

    fun demoteAdmin(memberId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true) }
            val result = manageGroupUseCase.demoteAdmin(groupId, memberId)
            result.fold(
                onSuccess = { msg ->
                    _uiState.update { it.copy(actionInProgress = false) }
                    _eventFlow.emit(GroupInfoEvent.ShowSnackbar(msg))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = error.localizedMessage ?: "Failed to demote admin"
                        )
                    }
                }
            )
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, showLeaveGroupConfirmation = false) }
            val result = manageGroupUseCase.leaveGroup(groupId)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionInProgress = false) }
                    _eventFlow.emit(GroupInfoEvent.GroupLeft)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = error.localizedMessage ?: "Failed to leave group"
                        )
                    }
                }
            )
        }
    }
}
