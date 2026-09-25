package com.oma.chat.presentation.settings

import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oma.chat.presentation.components.OmaAvatar
import com.oma.chat.presentation.components.OmaSearchBar
import com.oma.chat.presentation.components.OmaSectionHeader
import com.oma.chat.presentation.components.OmaSettingsRow
import com.oma.chat.presentation.theme.EmeraldPrimary
import com.oma.chat.presentation.theme.ErrorRed
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToPrivacy: () -> Unit = {},
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is SettingsEvent.LoggedOut -> onLoggedOut()
                is SettingsEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Profile Header Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToProfile)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        OmaAvatar(
                            avatarUrl = uiState.user?.avatar,
                            name = uiState.user?.name ?: "User",
                            size = 60.dp
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.user?.name ?: "User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.user?.bio?.ifBlank { "Hey there! I am using OMA-CHAT" } ?: "Hey there! I am using OMA-CHAT",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!uiState.user?.username.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@${uiState.user?.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(onClick = onNavigateToProfile) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Profile",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // 2. Account & Security Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        OmaSectionHeader(title = "Account")

                        OmaSettingsRow(
                            icon = Icons.Default.Key,
                            title = "Security & Password",
                            subtitle = "Update password and security credentials",
                            onClick = { viewModel.setShowChangePasswordDialog(true) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        OmaSettingsRow(
                            icon = Icons.Default.Lock,
                            title = "Privacy",
                            subtitle = "Last seen, profile photo, read receipts, blocked users",
                            onClick = onNavigateToPrivacy
                        )
                    }
                }
            }

            // 3. Chats & Notifications Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        OmaSectionHeader(title = "Preferences")

                        OmaSettingsRow(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            title = "Chats & Theme",
                            subtitle = "Theme, wallpapers, chat history",
                            onClick = { viewModel.setShowThemeDialog(true) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        OmaSettingsRow(
                            icon = Icons.Default.Notifications,
                            title = "Notifications",
                            subtitle = "Message, group & call tones",
                            onClick = { viewModel.toggleNotifications(!uiState.isNotificationsEnabled) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        OmaSettingsRow(
                            icon = Icons.Default.DataUsage,
                            title = "Storage and data",
                            subtitle = "Cache & local isolated storage",
                            onClick = { viewModel.setShowClearChatsDialog(true) }
                        )
                    }
                }
            }

            // 4. Diagnostics & Support Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        OmaSectionHeader(title = "Tools & Support")

                        OmaSettingsRow(
                            icon = Icons.Default.Info,
                            title = "Diagnostics & Test Center",
                            subtitle = "Test audio, WebRTC STUN/TURN, console",
                            onClick = onNavigateToDiagnostics
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        OmaSettingsRow(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = "Help & Feedback",
                            subtitle = "Report an issue, terms & policies",
                            onClick = { viewModel.setShowReportIssueDialog(true) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        OmaSettingsRow(
                            icon = Icons.Default.People,
                            title = "Invite a friend",
                            subtitle = "Share OMA-CHAT NxtGen",
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Let's chat on OMA-CHAT! Download now: https://api.pdktdev.in")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Invite via"))
                            }
                        )
                    }
                }
            }

            // 5. Logout Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OmaSettingsRow(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        iconTint = ErrorRed,
                        iconBgColor = ErrorRed.copy(alpha = 0.12f),
                        title = "Log out",
                        titleColor = ErrorRed,
                        subtitle = "Sign out from this device",
                        showChevron = false,
                        onClick = { viewModel.setShowLogoutConfirmDialog(true) }
                    )
                }
            }

            // 6. Brand Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "from",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "OMA-CHAT NxtGen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v2.0 Native Android Client",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // --- Dialogs ---

    // Theme Picker Dialog
    if (uiState.showThemeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowThemeDialog(false) },
            title = { Text("Choose theme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ThemeOptionRow(
                        title = "System default",
                        selected = uiState.selectedTheme == AppThemeSetting.SYSTEM,
                        onSelect = { viewModel.setTheme(AppThemeSetting.SYSTEM) }
                    )
                    ThemeOptionRow(
                        title = "Light",
                        selected = uiState.selectedTheme == AppThemeSetting.LIGHT,
                        onSelect = { viewModel.setTheme(AppThemeSetting.LIGHT) }
                    )
                    ThemeOptionRow(
                        title = "Dark",
                        selected = uiState.selectedTheme == AppThemeSetting.DARK,
                        onSelect = { viewModel.setTheme(AppThemeSetting.DARK) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowThemeDialog(false) }) {
                    Text("Cancel", color = EmeraldPrimary)
                }
            }
        )
    }

    // Change Password Dialog
    if (uiState.showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowChangePasswordDialog(false) },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = uiState.oldPassword,
                        onValueChange = { viewModel.onOldPasswordChanged(it) },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.newPassword,
                        onValueChange = { viewModel.onNewPasswordChanged(it) },
                        label = { Text("New Password (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitChangePassword() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Update")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowChangePasswordDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Report Issue / Feedback Dialog
    if (uiState.showReportIssueDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowReportIssueDialog(false) },
            title = { Text("Report an Issue / Feedback", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Describe your issue or suggestions to the OMA support team:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.reportReason,
                        onValueChange = { viewModel.onReportReasonChanged(it) },
                        placeholder = { Text("Enter details...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitReportIssue() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Submit")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowReportIssueDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Chats Dialog
    if (uiState.showClearChatsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowClearChatsDialog(false) },
            title = { Text("Storage & Data", fontWeight = FontWeight.Bold) },
            text = {
                Text("All local media cache and message storage are managed securely within your isolated account space.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.setShowClearChatsDialog(false) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Logout Confirmation Dialog
    if (uiState.showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowLogoutConfirmDialog(false) },
            title = { Text("Log out of OMA-CHAT?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You will need to re-authenticate with your username and password to log back in.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setShowLogoutConfirmDialog(false)
                        viewModel.logout()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Log Out", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowLogoutConfirmDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}
