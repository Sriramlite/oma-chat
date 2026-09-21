package com.oma.chat.presentation.settings

import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReportProblem
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.oma.chat.presentation.theme.EmeraldPrimary
import com.oma.chat.presentation.theme.ErrorRed
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

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
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search settings...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Settings",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Profile Header Card (WhatsApp Style)
            item {
                ProfileHeaderCard(
                    avatar = uiState.user?.avatar ?: "",
                    name = uiState.user?.name ?: "User",
                    status = uiState.user?.bio?.ifBlank { "Hey there! I am using OMA-CHAT" } ?: "Hey there! I am using OMA-CHAT",
                    username = uiState.user?.username ?: "",
                    onClick = onNavigateToProfile
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // 2. Settings Items List
            item {
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = "Account",
                    subtitle = "Security notifications, change password, delete account",
                    onClick = { viewModel.setShowChangePasswordDialog(true) }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy",
                    subtitle = "Block contacts, read receipts, disappearing messages",
                    onClick = { viewModel.setShowReportIssueDialog(true) }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "Chats",
                    subtitle = "Theme, wallpapers, chat history",
                    onClick = { viewModel.setShowThemeDialog(true) }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Message, group & call tones, vibration",
                    onClick = {
                        viewModel.toggleNotifications(!uiState.isNotificationsEnabled)
                    }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.DataUsage,
                    title = "Storage and data",
                    subtitle = "Network usage, auto-download",
                    onClick = {
                        viewModel.setShowClearChatsDialog(true)
                    }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "App language",
                    subtitle = "English (device's language)",
                    onClick = {}
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    iconTint = EmeraldPrimary,
                    title = "Diagnostics & Test Center",
                    subtitle = "Test microphone, audio levels, WebRTC STUN & console",
                    onClick = onNavigateToDiagnostics
                )
            }

            item {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = "Help & Support",
                    subtitle = "Help center, report an issue, privacy policy",
                    onClick = { viewModel.setShowReportIssueDialog(true) }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.People,
                    title = "Invite a friend",
                    subtitle = "Share OMA-CHAT with your friends",
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

            // 3. Logout Item
            item {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = ErrorRed,
                    title = "Log out",
                    titleColor = ErrorRed,
                    subtitle = "Sign out of your account on this device",
                    onClick = { viewModel.setShowLogoutConfirmDialog(true) }
                )
            }

            // 4. WhatsApp-style Footer
            item {
                Spacer(modifier = Modifier.height(24.dp))
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
                        text = "v1.0.0 (Native Android)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
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
                        text = "Describe your issue or suggestions to the OMA moderation and support team:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.reportReason,
                        onValueChange = { viewModel.onReportReasonChanged(it) },
                        placeholder = { Text("Enter details...") },
                        maxLines = 4,
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
private fun ProfileHeaderCard(
    avatar: String,
    name: String,
    status: String,
    username: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Large Avatar with Emerald border
            com.oma.chat.presentation.common.avatar.AvatarImage(
                avatar = avatar,
                name = name,
                size = 64.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Name & Status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (username.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // QR Code / Forward Icon shortcut
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "QR Code",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
