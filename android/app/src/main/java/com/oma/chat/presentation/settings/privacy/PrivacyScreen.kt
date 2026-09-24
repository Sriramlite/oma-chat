package com.oma.chat.presentation.settings.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oma.chat.presentation.theme.EmeraldPrimary
import kotlinx.coroutines.flow.collectLatest

private val SectionHeaderColor = Color(0xFF60A5FA)
private val DarkCardBg = Color(0xFF1E293B)
private val DarkCardBorder = Color(0xFF334155)
private val SubtitleColor = Color(0xFF94A3B8)
private val TitleColor = Color(0xFFF1F5F9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is PrivacyEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy",
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // --- VISIBILITY SECTION ---
            item {
                PrivacySectionHeader(title = "VISIBILITY")
            }

            item {
                PrivacyDropdownItem(
                    title = "Last Seen",
                    subtitle = "Who can see your last seen status",
                    selectedValue = uiState.lastSeenPrivacy,
                    onOptionSelected = { viewModel.updateLastSeen(it) }
                )
            }

            item {
                PrivacyDropdownItem(
                    title = "Profile Photo",
                    subtitle = "Who can see your profile picture",
                    selectedValue = uiState.profilePhotoPrivacy,
                    onOptionSelected = { viewModel.updateProfilePhoto(it) }
                )
            }

            item {
                PrivacyDropdownItem(
                    title = "About / Bio",
                    subtitle = "Who can see your bio",
                    selectedValue = uiState.aboutPrivacy,
                    onOptionSelected = { viewModel.updateAbout(it) }
                )
                PrivacyDivider()
            }

            // --- MESSAGING SECTION ---
            item {
                PrivacySectionHeader(title = "MESSAGING")
            }

            item {
                PrivacySwitchItem(
                    title = "Read Receipts",
                    subtitle = "If turned off, you won't send or receive read receipts.",
                    checked = uiState.readReceipts,
                    onCheckedChange = { viewModel.toggleReadReceipts(it) }
                )
                PrivacyDivider()
            }

            // --- LIVE STATUS SECTION ---
            item {
                PrivacySectionHeader(title = "LIVE STATUS")
            }

            item {
                PrivacySwitchItem(
                    title = "Share Battery Status",
                    subtitle = "Allow others to see your battery level.",
                    checked = uiState.shareBattery,
                    onCheckedChange = { viewModel.toggleShareBattery(it) }
                )
                PrivacyDivider()
            }

            // --- CONNECTIONS SECTION ---
            item {
                PrivacySectionHeader(title = "CONNECTIONS")
            }

            item {
                PrivacyNavigationItem(
                    title = "Blocked Users",
                    subtitle = "${uiState.blockedUsersCount} users",
                    onClick = onNavigateToBlockedUsers
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PrivacySectionHeader(title: String) {
    Text(
        text = title,
        color = SectionHeaderColor,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun PrivacyDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun PrivacyDropdownItem(
    title: String,
    subtitle: String,
    selectedValue: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val displayLabel = when (selectedValue.lowercase()) {
        "contacts" -> "My Contacts"
        "nobody" -> "Nobody"
        else -> "Everyone"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TitleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SubtitleColor,
                lineHeight = 16.sp
            )
        }

        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkCardBg,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TitleColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select",
                        tint = SubtitleColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Everyone") },
                    onClick = {
                        onOptionSelected("everyone")
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("My Contacts") },
                    onClick = {
                        onOptionSelected("contacts")
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Nobody") },
                    onClick = {
                        onOptionSelected("nobody")
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PrivacySwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TitleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SubtitleColor,
                lineHeight = 16.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmeraldPrimary,
                uncheckedThumbColor = Color(0xFFCBD5E1),
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}

@Composable
private fun PrivacyNavigationItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TitleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SubtitleColor
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Navigate",
            tint = SubtitleColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
