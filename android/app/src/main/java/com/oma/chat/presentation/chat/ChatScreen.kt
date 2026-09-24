package com.oma.chat.presentation.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.oma.chat.presentation.common.wallpaper.BookshelfWallpaper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.oma.chat.domain.model.Message
import com.oma.chat.domain.model.MessageStatus
import com.oma.chat.presentation.theme.DarkIncomingBubble
import com.oma.chat.presentation.theme.DarkOutgoingBubble
import com.oma.chat.presentation.theme.EmeraldPrimary
import com.oma.chat.presentation.theme.ErrorRed
import com.oma.chat.presentation.theme.LightIncomingBubble
import com.oma.chat.presentation.theme.LightOutgoingBubble
import com.oma.chat.presentation.theme.StatusOnline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.oma.chat.domain.model.CallType

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToGroupInfo: (groupId: String) -> Unit = {},
    onStartCall: (targetId: String, targetName: String, targetAvatar: String, callType: CallType) -> Unit = { _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showTopMenu by remember { mutableStateOf(false) }
    var showDeleteChatConfirm by remember { mutableStateOf(false) }
    var editingTargetMessage by remember { mutableStateOf<Message?>(null) }
    var pendingCallType by remember { mutableStateOf<CallType?>(null) }
    var showBatterySlide by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.partnerBatteryLevel) {
        if (uiState.partnerBatteryLevel != null) {
            while (isActive) {
                delay(3500)
                showBatterySlide = !showBatterySlide
            }
        } else {
            showBatterySlide = false
        }
    }

    val lastSeenText = when {
        uiState.isPartnerOnline -> "online"
        uiState.partnerLastSeen > 0L -> {
            val diff = System.currentTimeMillis() - uiState.partnerLastSeen
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            when {
                seconds < 60 -> "Last seen just now"
                minutes < 60 -> "Last seen ${minutes}m ago"
                hours < 24 -> "Last seen ${hours}h ago"
                days == 1L -> "Last seen yesterday"
                days < 7 -> "Last seen ${days}d ago"
                else -> "Last seen Long time ago"
            }
        }
        else -> "offline"
    }

    val batteryText = if (uiState.partnerBatteryLevel != null) {
        if (uiState.isPartnerCharging) {
            "⚡ ${uiState.partnerBatteryLevel}% Charging"
        } else {
            "🔋 ${uiState.partnerBatteryLevel}%"
        }
    } else null

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val targetCallType = pendingCallType
        pendingCallType = null

        if (targetCallType == CallType.VOICE) {
            if (audioGranted) {
                onStartCall(viewModel.chatId, uiState.chatName, uiState.partnerAvatar, CallType.VOICE)
            } else {
                Toast.makeText(context, "Microphone permission is required to make a voice call", Toast.LENGTH_LONG).show()
            }
        } else if (targetCallType == CallType.VIDEO) {
            if (audioGranted && cameraGranted) {
                onStartCall(viewModel.chatId, uiState.chatName, uiState.partnerAvatar, CallType.VIDEO)
            } else {
                Toast.makeText(context, "Microphone and Camera permissions are required to make a video call", Toast.LENGTH_LONG).show()
            }
        }
    }

    val requestCallWithPermissions = { callType: CallType ->
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (callType == CallType.VOICE) {
            if (hasAudio) {
                onStartCall(viewModel.chatId, uiState.chatName, uiState.partnerAvatar, CallType.VOICE)
            } else {
                pendingCallType = CallType.VOICE
                callPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        } else {
            if (hasAudio && hasCamera) {
                onStartCall(viewModel.chatId, uiState.chatName, uiState.partnerAvatar, CallType.VIDEO)
            } else {
                pendingCallType = CallType.VIDEO
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                callPermissionLauncher.launch(perms.toTypedArray())
            }
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { onNavigateToGroupInfo(viewModel.chatId) }
                    ) {
                        Box {
                            if (uiState.partnerAvatar.isNotBlank()) {
                                AsyncImage(
                                    model = uiState.partnerAvatar,
                                    contentDescription = uiState.chatName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (uiState.isPartnerOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(1.5.dp)
                                        .clip(CircleShape)
                                        .background(StatusOnline)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = uiState.chatName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.isPartnerTyping) {
                                Text(
                                    text = "typing...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (batteryText != null) {
                                AnimatedContent(
                                    targetState = showBatterySlide,
                                    transitionSpec = {
                                        (slideInVertically(
                                            animationSpec = tween(400, easing = FastOutSlowInEasing),
                                            initialOffsetY = { fullHeight -> fullHeight }
                                        ) + fadeIn(animationSpec = tween(400))).togetherWith(
                                            slideOutVertically(
                                                animationSpec = tween(400, easing = FastOutSlowInEasing),
                                                targetOffsetY = { fullHeight -> -fullHeight }
                                            ) + fadeOut(animationSpec = tween(400))
                                        )
                                    },
                                    label = "header_status_slide"
                                ) { isBattery ->
                                    if (isBattery) {
                                        Text(
                                            text = batteryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (uiState.isPartnerCharging) Color(0xFFF59E0B) else EmeraldPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Text(
                                            text = lastSeenText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (uiState.isPartnerOnline) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = lastSeenText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isPartnerOnline) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            requestCallWithPermissions(CallType.VOICE)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            requestCallWithPermissions(CallType.VIDEO)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Group Info") },
                                onClick = {
                                    showTopMenu = false
                                    onNavigateToGroupInfo(viewModel.chatId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Chat", color = ErrorRed) },
                                onClick = {
                                    showTopMenu = false
                                    showDeleteChatConfirm = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = ErrorRed
                                    )
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Default Animated Bookshelf Wallpaper
            BookshelfWallpaper(modifier = Modifier.fillMaxSize())

            // Subtle dark overlay to ensure maximum message contrast and readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f))
            )

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    val isOutgoing = message.senderId == viewModel.ownerUserId
                    MessageBubble(
                        message = message,
                        isOutgoing = isOutgoing,
                        onLongClick = { viewModel.selectMessage(message) }
                    )
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.messageInput,
                        onValueChange = { viewModel.onMessageInputChange(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { viewModel.sendMessage() }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.sendMessage() },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Message Actions Bottom Sheet
        uiState.selectedMessage?.let { selectedMsg ->
            val isMyMessage = selectedMsg.senderId == viewModel.ownerUserId
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectMessage(null) },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Message Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Copy
                    ActionRow(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy Text",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OMA Message", selectedMsg.content))
                            viewModel.selectMessage(null)
                        }
                    )

                    // Star / Unstar
                    ActionRow(
                        icon = if (selectedMsg.isStarred) Icons.Default.StarBorder else Icons.Default.Star,
                        label = if (selectedMsg.isStarred) "Unstar" else "Star",
                        onClick = {
                            viewModel.toggleStar(selectedMsg.id, selectedMsg.isStarred)
                        }
                    )

                    // Pin / Unpin
                    ActionRow(
                        icon = if (selectedMsg.isPinned) Icons.Default.PinDrop else Icons.Default.PushPin,
                        label = if (selectedMsg.isPinned) "Unpin" else "Pin",
                        onClick = {
                            viewModel.togglePin(selectedMsg.id, selectedMsg.isPinned)
                        }
                    )

                    // Edit (only if my message and not deleted)
                    if (isMyMessage && !selectedMsg.isDeleted) {
                        ActionRow(
                            icon = Icons.Default.Edit,
                            label = "Edit Message",
                            onClick = {
                                editingTargetMessage = selectedMsg
                                viewModel.startEditingMessage(selectedMsg)
                            }
                        )
                    }

                    // Delete for me
                    ActionRow(
                        icon = Icons.Default.Delete,
                        label = "Delete for me",
                        color = ErrorRed,
                        onClick = {
                            viewModel.deleteMessage(selectedMsg.id, "me")
                        }
                    )

                    // Delete for everyone (if my message)
                    if (isMyMessage && !selectedMsg.isDeleted) {
                        ActionRow(
                            icon = Icons.Default.DeleteForever,
                            label = "Delete for everyone",
                            color = ErrorRed,
                            onClick = {
                                viewModel.deleteMessage(selectedMsg.id, "everyone")
                            }
                        )
                    }
                }
            }
        }

        // Edit Message Dialog
        if (uiState.isEditing && editingTargetMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelEditing() },
                title = { Text("Edit Message") },
                text = {
                    OutlinedTextField(
                        value = uiState.editingText,
                        onValueChange = { viewModel.onEditingTextChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            editingTargetMessage?.let { viewModel.confirmEditMessage(it.id) }
                        }
                    ) {
                        Text("Save", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelEditing() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Entire Chat Confirmation Dialog
        if (showDeleteChatConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteChatConfirm = false },
                title = { Text("Delete Entire Chat?") },
                text = { Text("This will permanently remove all messages in this conversation for everyone. This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteChatConfirm = false
                            viewModel.deleteChat(onDeleted = onNavigateBack)
                        }
                    ) {
                        Text("Delete", color = ErrorRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteChatConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    onLongClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bubbleColor = when {
        isOutgoing -> if (isDark) DarkOutgoingBubble else LightOutgoingBubble
        else -> if (isDark) DarkIncomingBubble else LightIncomingBubble
    }
    val textColor = if (isOutgoing && !isDark) Color(0xFF111B21) else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isOutgoing) 14.dp else 2.dp,
                bottomEnd = if (isOutgoing) 2.dp else 14.dp
            ),
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                // Pin / Star indicators
                if (message.isPinned || message.isStarred) {
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (message.isStarred) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Starred",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isDeleted) textColor.copy(alpha = 0.5f) else textColor,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            text = "edited",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = textColor.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.6f)
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.PENDING, MessageStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Pending",
                                    tint = textColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = textColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = textColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.SEEN -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Seen",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                Text(
                                    text = "!",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
