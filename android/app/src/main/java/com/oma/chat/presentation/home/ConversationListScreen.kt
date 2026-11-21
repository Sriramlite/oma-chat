package com.oma.chat.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oma.chat.R
import com.oma.chat.data.mesh.BlePeer
import com.oma.chat.domain.model.CallType
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
    onStartCall: (targetId: String, targetName: String, targetAvatar: String, callType: CallType) -> Unit = { _, _, _, _ -> },
    onLoggedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isBleMeshMode by viewModel.isBleMeshMode.collectAsState()
    val meshPeers by viewModel.meshPeers.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var inlineFilterQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = uiState.currentTab.ordinal,
        pageCount = { 4 }
    )

    // Synchronize pager swipe to ViewModel
    LaunchedEffect(pagerState.currentPage) {
        val tab = MainTab.entries.getOrElse(pagerState.currentPage) { MainTab.CHATS }
        if (uiState.currentTab != tab) {
            viewModel.selectTab(tab)
        }
    }
    // Synchronize ViewModel tab changes to pager
    LaunchedEffect(uiState.currentTab) {
        if (pagerState.currentPage != uiState.currentTab.ordinal) {
            pagerState.scrollToPage(uiState.currentTab.ordinal)
        }
    }

    val activeTab = MainTab.entries.getOrElse(pagerState.currentPage) { MainTab.CHATS }

    val categories = listOf("All", "Unread", "Groups")

    // Filter conversations based on selected category chip & inline search
    val filteredConversations = remember(uiState.conversations, selectedCategory, inlineFilterQuery) {
        uiState.conversations.filter { conv ->
            val matchesCategory = when (selectedCategory) {
                "Unread" -> conv.unreadCount > 0
                "Groups" -> conv.type == "group"
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
                    when (activeTab) {
                        MainTab.CHATS -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
                                    Text(
                                        text = if (isBleMeshMode) "BLE Mesh (${meshPeers.size} peers)" else "Online",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isBleMeshMode) EmeraldPrimary else StatusOnline,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        MainTab.CALLS -> {
                            Text(
                                text = "Calls",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        MainTab.OFFLINE_SOCIAL -> {
                            Text(
                                text = "Social & Offline",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        MainTab.PROFILE -> {
                            Text(
                                text = "User Profile",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    if (activeTab == MainTab.CHATS) {
                        IconButton(onClick = onNavigateToCreateGroup) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = "New Group",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (activeTab == MainTab.OFFLINE_SOCIAL) {
                        IconButton(onClick = { viewModel.restartMeshScan() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart Scan",
                                tint = EmeraldPrimary
                            )
                        }
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
            if (activeTab == MainTab.CHATS) {
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
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                // Tab 1: Recent chats
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        coroutineScope.launch { pagerState.scrollToPage(0) }
                        viewModel.selectTab(MainTab.CHATS)
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                val unread = uiState.conversations.sumOf { it.unreadCount }
                                if (unread > 0) Badge { Text("$unread") }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Recent chats"
                            )
                        }
                    },
                    label = { Text("Chats") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldPrimary,
                        selectedTextColor = EmeraldPrimary,
                        indicatorColor = EmeraldPrimary.copy(alpha = 0.15f)
                    )
                )

                // Tab 2: Calls - INSTANT SWITCH with zero delay
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        coroutineScope.launch { pagerState.scrollToPage(1) }
                        viewModel.selectTab(MainTab.CALLS)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Calls"
                        )
                    },
                    label = { Text("Calls") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldPrimary,
                        selectedTextColor = EmeraldPrimary,
                        indicatorColor = EmeraldPrimary.copy(alpha = 0.15f)
                    )
                )

                // Tab 3: Social / Offline
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = {
                        coroutineScope.launch { pagerState.scrollToPage(2) }
                        viewModel.selectTab(MainTab.OFFLINE_SOCIAL)
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (meshPeers.isNotEmpty()) {
                                    Badge(containerColor = EmeraldPrimary) {
                                        Text("${meshPeers.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = "Social/Offline"
                            )
                        }
                    },
                    label = { Text("Social/Offline") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldPrimary,
                        selectedTextColor = EmeraldPrimary,
                        indicatorColor = EmeraldPrimary.copy(alpha = 0.15f)
                    )
                )

                // Tab 4: User Profile
                NavigationBarItem(
                    selected = pagerState.currentPage == 3,
                    onClick = {
                        coroutineScope.launch { pagerState.scrollToPage(3) }
                        viewModel.selectTab(MainTab.PROFILE)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Profile"
                        )
                    },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldPrimary,
                        selectedTextColor = EmeraldPrimary,
                        indicatorColor = EmeraldPrimary.copy(alpha = 0.15f)
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> {
                    ChatsTabContent(
                        inlineFilterQuery = inlineFilterQuery,
                        onQueryChange = { inlineFilterQuery = it },
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        conversations = filteredConversations,
                        onOpenNewChat = { viewModel.openNewChatDialog() },
                        onNavigateToChat = onNavigateToChat
                    )
                }
                1 -> {
                    CallsTabContent(
                        conversations = uiState.conversations,
                        onStartCall = onStartCall,
                        onBrowseContacts = {
                            coroutineScope.launch { pagerState.scrollToPage(2) }
                            viewModel.selectTab(MainTab.OFFLINE_SOCIAL)
                            viewModel.setSocialCategory(SocialCategory.CONTACTS)
                        },
                        onNavigateToCreateGroup = onNavigateToCreateGroup
                    )
                }
                2 -> {
                    OfflineSocialTabContent(
                        isBleMeshMode = isBleMeshMode,
                        meshPeers = meshPeers,
                        socialCategory = uiState.socialCategory,
                        contacts = uiState.contacts,
                        displayedContactsCount = uiState.displayedContactsCount,
                        isLoadingContacts = uiState.isLoadingContacts,
                        onlineUserIds = uiState.onlineUserIds,
                        onSelectCategory = { viewModel.setSocialCategory(it) },
                        onToggleBleMeshMode = { viewModel.toggleBleMeshMode(it) },
                        onRestartMeshScan = { viewModel.restartMeshScan() },
                        onLoadMoreContacts = { viewModel.loadMoreContacts() },
                        onNavigateToChat = onNavigateToChat,
                        onStartCall = onStartCall
                    )
                }
                3 -> {
                    ProfileTabContent(
                        currentUser = currentUser,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToSettings = onNavigateToSettings,
                        onLogout = {
                            viewModel.logout()
                            onLoggedOut()
                        }
                    )
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
private fun ChatsTabContent(
    inlineFilterQuery: String,
    onQueryChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    conversations: List<Conversation>,
    onOpenNewChat: () -> Unit,
    onNavigateToChat: (chatId: String, chatName: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Modern Search Bar
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            OmaSearchBar(
                query = inlineFilterQuery,
                onQueryChange = onQueryChange,
                placeholderText = "Search conversations...",
                onClear = { onQueryChange("") }
            )
        }

        // Horizontally scrollable category filter chips
        OmaCategoryChips(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (conversations.isEmpty() && !isRefreshing) {
                OmaEmptyState(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = if (inlineFilterQuery.isNotBlank()) "No conversations found" else "Start a conversation",
                    subtitle = if (inlineFilterQuery.isNotBlank()) "Try searching for a different name or message" else "Connect with friends and colleagues to start chatting on OMA-CHAT.",
                    actionButtonText = if (inlineFilterQuery.isBlank()) "Start Chat" else null,
                    onActionClick = onOpenNewChat
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = conversations,
                        key = { it.id }
                    ) { conversation ->
                        ModernConversationItem(
                            conversation = conversation,
                            onClick = { onNavigateToChat(conversation.id, conversation.name) },
                            modifier = Modifier.animateItem()
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallsTabContent(
    conversations: List<Conversation>,
    onStartCall: (targetId: String, targetName: String, targetAvatar: String, callType: CallType) -> Unit,
    onBrowseContacts: () -> Unit,
    onNavigateToCreateGroup: () -> Unit = {}
) {
    val callContacts = remember(conversations) { conversations.filter { it.type != "group" } }
    val groupChats = remember(conversations) { conversations.filter { it.type == "group" } }
    var isCallsLoading by remember { mutableStateOf(true) }
    var showGroupCallSheet by remember { mutableStateOf(false) }
    val groupCallSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        delay(250)
        isCallsLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Small sleek loading indicator on tab entry
        AnimatedVisibility(
            visible = isCallsLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = EmeraldPrimary,
                trackColor = EmeraldPrimary.copy(alpha = 0.12f)
            )
        }

        // Encrypted WebRTC audio & video banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = EmeraldPrimary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Encrypted WebRTC Audio & Video",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                    Text(
                        text = "High definition, low latency calls with STUN/TURN relays",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Dedicated Group Call Action Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGroupCallSheet = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldPrimary,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = "Group Call",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Group Call",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (groupChats.isNotEmpty()) "${groupChats.size} group(s) available • Instant conference" else "Start call with groups or multiple contacts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = { showGroupCallSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Call Group", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (callContacts.isEmpty()) {
            OmaEmptyState(
                icon = Icons.Default.Call,
                title = "No call history yet",
                subtitle = "Start a voice or video call with your contacts anytime.",
                actionButtonText = "Browse Contacts",
                onActionClick = onBrowseContacts
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(callContacts, key = { it.id }) { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OmaAvatar(
                            avatarUrl = conv.avatar,
                            name = conv.name,
                            size = 48.dp,
                            isOnline = conv.isOnline,
                            showOnlineBadge = true
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conv.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (conv.isOnline) "Online" else "Recent chat contact",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (conv.isOnline) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                onStartCall(conv.id, conv.name, conv.avatar, CallType.VOICE)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Voice Call",
                                    tint = EmeraldPrimary
                                )
                            }
                            IconButton(onClick = {
                                onStartCall(conv.id, conv.name, conv.avatar, CallType.VIDEO)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Video Call",
                                    tint = EmeraldPrimary
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }

    // Group Call Selection Sheet
    if (showGroupCallSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGroupCallSheet = false },
            sheetState = groupCallSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Start Group Call",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = {
                        showGroupCallSheet = false
                        onNavigateToCreateGroup()
                    }) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Group", color = EmeraldPrimary)
                    }
                }

                Text(
                    text = "Select a group to start an audio or video call with all members",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (groupChats.isEmpty()) {
                    OmaEmptyState(
                        icon = Icons.Default.GroupAdd,
                        title = "No group chats yet",
                        subtitle = "Create a group first to start conference calls with your friends or team.",
                        actionButtonText = "Create New Group",
                        onActionClick = {
                            showGroupCallSheet = false
                            onNavigateToCreateGroup()
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        items(groupChats, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OmaAvatar(
                                    avatarUrl = group.avatar,
                                    name = group.name,
                                    size = 46.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Group • Tap call button",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    showGroupCallSheet = false
                                    onStartCall(group.id, group.name, group.avatar, CallType.VOICE)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Group Voice Call",
                                        tint = EmeraldPrimary
                                    )
                                }
                                IconButton(onClick = {
                                    showGroupCallSheet = false
                                    onStartCall(group.id, group.name, group.avatar, CallType.VIDEO)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Group Video Call",
                                        tint = EmeraldPrimary
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 58.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineSocialTabContent(
    isBleMeshMode: Boolean,
    meshPeers: List<BlePeer>,
    socialCategory: SocialCategory,
    contacts: List<User>,
    displayedContactsCount: Int,
    isLoadingContacts: Boolean,
    onlineUserIds: Set<String>,
    onSelectCategory: (SocialCategory) -> Unit,
    onToggleBleMeshMode: (Boolean) -> Unit,
    onRestartMeshScan: () -> Unit,
    onLoadMoreContacts: () -> Unit,
    onNavigateToChat: (chatId: String, chatName: String) -> Unit,
    onStartCall: (targetId: String, targetName: String, targetAvatar: String, callType: CallType) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Two Category Segmented Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onSelectCategory(SocialCategory.NEARBY) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (socialCategory == SocialCategory.NEARBY) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (socialCategory == SocialCategory.NEARBY) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nearby (${meshPeers.size})", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { onSelectCategory(SocialCategory.CONTACTS) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (socialCategory == SocialCategory.CONTACTS) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (socialCategory == SocialCategory.CONTACTS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Contacts (${contacts.size})", fontWeight = FontWeight.SemiBold)
            }
        }

        AnimatedContent(
            targetState = socialCategory,
            transitionSpec = {
                (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.99f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(animationSpec = tween(150)))
            },
            label = "SocialCategoryAnimation"
        ) { category ->
            when (category) {
                SocialCategory.NEARBY -> {
                    NearbyCategoryContent(
                        isBleMeshMode = isBleMeshMode,
                        meshPeers = meshPeers,
                        onToggleBleMeshMode = onToggleBleMeshMode,
                        onRestartMeshScan = onRestartMeshScan,
                        onNavigateToChat = onNavigateToChat
                    )
                }
                SocialCategory.CONTACTS -> {
                    ContactsCategoryContent(
                        contacts = contacts,
                        displayedCount = displayedContactsCount,
                        isLoading = isLoadingContacts,
                        onlineUserIds = onlineUserIds,
                        onLoadMore = onLoadMoreContacts,
                        onNavigateToChat = onNavigateToChat,
                        onStartCall = onStartCall
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyCategoryContent(
    isBleMeshMode: Boolean,
    meshPeers: List<BlePeer>,
    onToggleBleMeshMode: (Boolean) -> Unit,
    onRestartMeshScan: () -> Unit,
    onNavigateToChat: (chatId: String, chatName: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // BLE Mesh Control Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isBleMeshMode) EmeraldPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                width = 1.dp,
                color = if (isBleMeshMode) EmeraldPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isBleMeshMode) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = if (isBleMeshMode) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline BLE Mesh Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isBleMeshMode) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isBleMeshMode) {
                                "Broadcasting & Scanning (5-Hop Relay)"
                            } else {
                                "Enable to send messages without internet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = isBleMeshMode,
                    onCheckedChange = onToggleBleMeshMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = EmeraldPrimary
                    )
                )
            }
        }

        // Section header with Scan Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby Peers (${meshPeers.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(onClick = onRestartMeshScan) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restart Scan",
                    tint = EmeraldPrimary
                )
            }
        }

        if (meshPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isBleMeshMode) "Searching for Nearby Devices..." else "Offline Mesh is Turned Off",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (isBleMeshMode) {
                            "Keep Bluetooth turned on. When another phone running OMA-CHAT comes within 10–30 meters, it appears here automatically. Offline messages hop across intermediate nodes."
                        } else {
                            "Turn on Offline BLE Mesh above to discover and message nearby peers directly without Wi-Fi or mobile data."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    if (!isBleMeshMode) {
                        Button(
                            onClick = { onToggleBleMeshMode(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Turn On BLE Mesh")
                        }
                    } else {
                        Button(
                            onClick = onRestartMeshScan,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Refresh Scan")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(meshPeers, key = { it.deviceAddress }) { peer ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = EmeraldPrimary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Bluetooth,
                                            contentDescription = null,
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = peer.deviceName.ifBlank { "OMA Mesh Node" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = peer.deviceAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (peer.rssi >= -70) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "${peer.rssi} dBm",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (peer.rssi >= -70) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }

                                        Text(
                                            text = if (peer.isConnected) "Connected" else "In Range",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (peer.isConnected) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val peerName = peer.deviceName.ifBlank { "Offline Peer (${peer.deviceAddress.takeLast(5)})" }
                                    onNavigateToChat(peer.deviceAddress, peerName)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chat Offline", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsCategoryContent(
    contacts: List<User>,
    displayedCount: Int,
    isLoading: Boolean,
    onlineUserIds: Set<String>,
    onLoadMore: () -> Unit,
    onNavigateToChat: (chatId: String, chatName: String) -> Unit,
    onStartCall: (targetId: String, targetName: String, targetAvatar: String, callType: CallType) -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= displayedCount - 2 && displayedCount < contacts.size
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "All Users on OMA-CHAT",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Showing ${minOf(displayedCount, contacts.size)} of ${contacts.size}",
                style = MaterialTheme.typography.bodySmall,
                color = EmeraldPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        if (contacts.isEmpty() && isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
        } else if (contacts.isEmpty()) {
            OmaEmptyState(
                icon = Icons.Default.People,
                title = "No contacts found",
                subtitle = "Registered users on OMA-CHAT will appear here."
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = contacts.take(displayedCount),
                    key = { it.id }
                ) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToChat(contact.id, contact.name) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OmaAvatar(
                            avatarUrl = contact.avatar,
                            name = contact.name,
                            size = 48.dp,
                            isOnline = onlineUserIds.contains(contact.id),
                            showOnlineBadge = true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!contact.username.isNullOrBlank()) {
                                Text(
                                    text = "@${contact.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldPrimary
                                )
                            }
                            if (!contact.bio.isNullOrBlank()) {
                                Text(
                                    text = contact.bio,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onNavigateToChat(contact.id, contact.name) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Message",
                                    tint = EmeraldPrimary
                                )
                            }
                            IconButton(onClick = {
                                onStartCall(contact.id, contact.name, contact.avatar, CallType.VOICE)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = EmeraldPrimary
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                if (displayedCount < contacts.size) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = onLoadMore) {
                                Text(
                                    text = "Load More (${contacts.size - displayedCount} remaining)",
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTabContent(
    currentUser: User?,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OmaAvatar(
                    avatarUrl = currentUser?.avatar,
                    name = currentUser?.name ?: "User",
                    size = 80.dp,
                    onClick = onNavigateToProfile
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = currentUser?.name ?: "User",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!currentUser?.username.isNullOrBlank()) {
                    Text(
                        text = "@${currentUser?.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = currentUser?.bio?.ifBlank { "Hey there! I am using OMA-CHAT" } ?: "Hey there! I am using OMA-CHAT",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Action Options
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToProfile)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Edit Profile & Avatar",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToSettings)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Settings & Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogout)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Log Out",
                        style = MaterialTheme.typography.titleMedium,
                        color = ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OmaAvatar(
            avatarUrl = conversation.avatar,
            name = conversation.name,
            size = 52.dp,
            isOnline = conversation.isOnline,
            showOnlineBadge = true
        )

        Spacer(modifier = Modifier.width(14.dp))

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
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
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
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
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
            .padding(bottom = 24.dp)
    ) {
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
