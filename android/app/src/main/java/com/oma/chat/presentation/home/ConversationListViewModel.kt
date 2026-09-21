package com.oma.chat.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.domain.model.Conversation
import com.oma.chat.domain.model.User
import com.oma.chat.domain.usecase.auth.GetCurrentUserUseCase
import com.oma.chat.domain.usecase.auth.LogoutUseCase
import com.oma.chat.domain.usecase.chat.GetRecentConversationsUseCase
import com.oma.chat.domain.usecase.chat.ObservePresenceUseCase
import com.oma.chat.domain.usecase.chat.RefreshRecentConversationsUseCase
import com.oma.chat.domain.usecase.chat.SearchUsersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    val onlineUserIds: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val showNewChatDialog: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val getRecentConversationsUseCase: GetRecentConversationsUseCase,
    private val refreshRecentConversationsUseCase: RefreshRecentConversationsUseCase,
    private val searchUsersUseCase: SearchUsersUseCase,
    private val observePresenceUseCase: ObservePresenceUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val ownerUserId: String = authPreferences.getUserId() ?: ""

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    val currentUser: StateFlow<User?> = getCurrentUserUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        // Connect Socket.IO upon entering home
        observePresenceUseCase.connect()

        // Combine Room conversation flow with real-time presence set
        viewModelScope.launch {
            combine(
                getRecentConversationsUseCase(ownerUserId),
                observePresenceUseCase()
            ) { conversations, onlineUsers ->
                conversations.map { conv ->
                    conv.copy(isOnline = onlineUsers.contains(conv.id))
                } to onlineUsers
            }.collect { (conversations, onlineUsers) ->
                _uiState.update {
                    it.copy(
                        conversations = conversations,
                        onlineUserIds = onlineUsers
                    )
                }
            }
        }

        // Fetch authoritative recent chats from backend
        refresh()
    }

    fun refresh() {
        if (ownerUserId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = refreshRecentConversationsUseCase(ownerUserId)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isRefreshing = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.localizedMessage
                        )
                    }
                }
            )
        }
    }

    fun openNewChatDialog() {
        _uiState.update { it.copy(showNewChatDialog = true, searchQuery = "", searchResults = emptyList()) }
    }

    fun closeNewChatDialog() {
        _uiState.update { it.copy(showNewChatDialog = false, searchQuery = "", searchResults = emptyList()) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            searchUsers(query)
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    private fun searchUsers(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val result = searchUsersUseCase(query)
            result.fold(
                onSuccess = { users ->
                    // Exclude self from search results
                    val filtered = users.filter { it.id != ownerUserId }
                    _uiState.update { it.copy(searchResults = filtered, isSearching = false) }
                },
                onFailure = {
                    _uiState.update { it.copy(isSearching = false) }
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            observePresenceUseCase.disconnect()
            logoutUseCase()
        }
    }
}
