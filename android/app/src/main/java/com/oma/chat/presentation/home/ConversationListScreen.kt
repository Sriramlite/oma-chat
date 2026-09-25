package com.oma.chat.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oma.chat.domain.model.Conversation
import com.oma.chat.domain.model.User
import com.oma.chat.presentation.components.OmaAvatar
import com.oma.chat.presentation.components.OmaCategoryChips
import com.oma.chat.presentation.components.OmaEmptyState
import com.oma.chat.presentation.components.OmaSearchBar
import com.oma.chat.presentation.theme.EmeraldDark
import com.oma.chat.presentation.theme.EmeraldPrimary
import com.oma.chat.presentation.theme.ErrorRed
import com.oma.chat.presentation.theme.StatusOnline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel = hiltViewModel(),
    onNavigateToChat: (chatId: String, chatName: String) -> Unit,
    onNavigateToCreateGroup: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onLoggedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var inlineFilterQuery by remember { mutableStateOf("") }

    val categories = listOf("All", "Unread", "Groups", "Calls")

    // Filter conversations based on selected category chip & inline search
    val filteredConversations = remember(uiState.conversations, selectedCategory, inlineFilterQuery) {
        uiState.conversations.filter { conv ->
            val matchesCategory = when (selectedCategory) {
                "Unread" -> conv.unreadCount > 0
                "Groups" -> conv.type == "group"
                "Calls" -> true
                else -> true
            }
            val matchesQuery = if (inlineFilterQuery.isBlank()) {
                true
            } else {
                conv.name.contains(inlineFilterQuery, ignoreCase = true) ||
                        conv.lastMsg.contains(inlineFilterQuery, ignoreCase = true) ||
                        (conv.username?.contains(inlineFilterQuery, ignoreCase = true) == true)
            }
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // User Avatar button for quick profile access
                        OmaAvatar(
                            avatarUrl = currentUser?.avatar,
                            name = currentUser?.name ?: "User",
                            size = 36.dp,
                            onClick = onNavigateToProfile
                        )

                        Column {
                            Text(
                                text = "OMA-CHAT",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCreateGroup) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "New Group",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New group") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToCreateGroup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Profile") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToProfile()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Log out", color = ErrorRed) },
                                onClick = {
                                    showMenu = false
                                    viewModel.logout()
                                    onLoggedOut()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openNewChatDialog() },
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = "New Chat"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Modern Search Bar
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OmaSearchBar(
                    query = inlineFilterQuery,
                    onQueryChange = { inlineFilterQuery = it },
                    placeholderText = "Search conversations...",
                    onClear = { inlineFilterQuery = "" }
                )
            }

            // Horizontally scrollable category filter chips
            OmaCategoryChips(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredConversations.isEmpty() && !uiState.isRefreshing) {
                    OmaEmptyState(
                        icon = Icons.Default.ChatBubbleOutline,
                        title = if (inlineFilterQuery.isNotBlank()) "No conversations found" else "Start a conversation",
                        subtitle = if (inlineFilterQuery.isNotBlank()) "Try searching for a different name or message" else "Connect with friends and colleagues to start chatting on OMA-CHAT.",
                        actionButtonText = if (inlineFilterQuery.isBlank()) "Start Chat" else null,
                        onActionClick = { viewModel.openNewChatDialog() }
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = filteredConversations,
                            key = { it.id }
                        ) { conversation ->
                            ModernConversationItem(
                                conversation = conversation,
                                onClick = { onNavigateToChat(conversation.id, conversation.name) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 78.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }

        // New Chat User Search Bottom Sheet
        if (uiState.showNewChatDialog) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeNewChatDialog() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NewChatSearchContent(
                    searchQuery = uiState.searchQuery,
                    searchResults = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onNewGroupClick = {
                        viewModel.closeNewChatDialog()
                        onNavigateToCreateGroup()
                    },
                    onUserSelected = { user ->
                        viewModel.closeNewChatDialog()
                        onNavigateToChat(user.id, user.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun ModernConversationItem(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with Online indicator
        OmaAvatar(
            avatarUrl = conversation.avatar,
            name = conversation.name,
            size = 52.dp,
            isOnline = conversation.isOnline,
            showOnlineBadge = true
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Conversation details
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatTimestamp(conversation.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.unreadCount > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (conversation.lastMsg.isNotBlank()) conversation.lastMsg else "No messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conversation.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChatSearchContent(
    searchQuery: String,
    searchResults: List<User>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onNewGroupClick: () -> Unit,
    onUserSelected: (User) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Sheet Header
        Text(
            text = "New Conversation",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Search Field
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OmaSearchBar(
                query = searchQuery,
                onQueryChange = onQueryChange,
                placeholderText = "Search by name or username..."
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // New Group Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNewGroupClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = "New Group",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "New Group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Create a group conversation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Search Results List
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = EmeraldPrimary,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        } else if (searchResults.isEmpty() && searchQuery.length >= 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No users found matching \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(
                    items = searchResults,
                    key = { it.id }
                ) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUserSelected(user) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OmaAvatar(
                            avatarUrl = user.avatar,
                            name = user.name,
                            size = 46.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "@${user.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val date = Date(timestamp)

    return when {
        diff < 24 * 60 * 60 * 1000L -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        }
        diff < 48 * 60 * 60 * 1000L -> "Yesterday"
        diff < 7 * 24 * 60 * 60 * 1000L -> {
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        }
        else -> {
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
        }
    }
}
