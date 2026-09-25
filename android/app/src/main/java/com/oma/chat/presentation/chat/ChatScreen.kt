package com.oma.chat.presentation.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.oma.chat.domain.model.CallType
import com.oma.chat.domain.model.Message
import com.oma.chat.domain.model.MessageStatus
import com.oma.chat.presentation.common.wallpaper.BookshelfWallpaper
import com.oma.chat.presentation.components.BatteryStatusBadge
import com.oma.chat.presentation.components.OmaAvatar
import com.oma.chat.presentation.components.OmaTypingDots
import com.oma.chat.presentation.theme.DarkIncomingBubble
import com.oma.chat.presentation.theme.DarkOutgoingBubble
import com.oma.chat.presentation.theme.EmeraldPrimary
import com.oma.chat.presentation.theme.ErrorRed
import com.oma.chat.presentation.theme.LightIncomingBubble
import com.oma.chat.presentation.theme.LightOutgoingBubble
import com.oma.chat.presentation.theme.StatusOnline
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToGroupInfo: (groupId: String) -> Unit = {},
    onNavigateToUserProfile: (userId: String) -> Unit = {},
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
    var showEmojiSheet by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            Toast.makeText(context, "Attachment selected: ${it.lastPathSegment}", Toast.LENGTH_SHORT).show()
            viewModel.onMessageInputChange(uiState.messageInput + " [Attachment: ${it.lastPathSegment}] ")
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            Toast.makeText(context, "Document selected: ${it.lastPathSegment}", Toast.LENGTH_SHORT).show()
            viewModel.onMessageInputChange(uiState.messageInput + " [Document: ${it.lastPathSegment}] ")
        }
    }

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
                            .clickable {
                                if (viewModel.chatId.startsWith("group_")) {
                                    onNavigateToGroupInfo(viewModel.chatId)
                                } else {
                                    onNavigateToUserProfile(viewModel.chatId)
                                }
                            }
                    ) {
                        OmaAvatar(
                            avatarUrl = uiState.partnerAvatar,
                            name = uiState.chatName,
                            size = 40.dp,
                            isOnline = uiState.isPartnerOnline,
                            showOnlineBadge = true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = uiState.chatName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.isPartnerTyping) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "typing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    OmaTypingDots(dotSize = 3.5.dp)
                                }
                            } else if (uiState.partnerBatteryLevel != null) {
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
                                        BatteryStatusBadge(
                                            level = uiState.partnerBatteryLevel ?: 100,
                                            isCharging = uiState.isPartnerCharging,
                                            textColor = EmeraldPrimary,
                                            fontSize = 12.sp
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
                        onClick = { requestCallWithPermissions(CallType.VOICE) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { requestCallWithPermissions(CallType.VIDEO) }
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
                            if (viewModel.chatId.startsWith("group_")) {
                                DropdownMenuItem(
                                    text = { Text("Group Info") },
                                    onClick = {
                                        showTopMenu = false
                                        onNavigateToGroupInfo(viewModel.chatId)
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("View Contact") },
                                    onClick = {
                                        showTopMenu = false
                                        onNavigateToUserProfile(viewModel.chatId)
                                    }
                                )
                            }
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

            // Subtle overlay to ensure maximum message contrast and readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Messages Stream
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
                        ModernMessageBubble(
                            message = message,
                            isOutgoing = isOutgoing,
                            onLongClick = { viewModel.selectMessage(message) }
                        )
                    }
                }

                // Partner typing banner indicator
                AnimatedVisibility(
                    visible = uiState.isPartnerTyping,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OmaTypingDots(dotSize = 5.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${uiState.chatName} is typing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Modern Bottom Composer Input Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Composer Capsule
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showEmojiSheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SentimentSatisfiedAlt,
                                        contentDescription = "Emoji",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (uiState.messageInput.isEmpty()) {
                                        Text(
                                            text = "Message...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    BasicTextField(
                                        value = uiState.messageInput,
                                        onValueChange = { viewModel.onMessageInputChange(it) },
                                        singleLine = false,
                                        maxLines = 4,
                                        textStyle = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(EmeraldPrimary),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Send
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSend = { viewModel.sendMessage() }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { showAttachmentSheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Smooth Send / Mic Button Transition
                        AnimatedContent(
                            targetState = uiState.messageInput.isNotBlank(),
                            transitionSpec = {
                                (scaleIn(animationSpec = tween(200)) + fadeIn()).togetherWith(
                                    scaleOut(animationSpec = tween(200)) + fadeOut()
                                )
                            },
                            label = "send_mic_transition"
                        ) { hasText ->
                            if (hasText) {
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
                            } else {
                                IconButton(
                                    onClick = {
                                        Toast.makeText(context, "Voice recording ready", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice Message",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
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

        // Emoji Selection Bottom Sheet
        if (showEmojiSheet) {
            EmojiPickerBottomSheet(
                onDismiss = { showEmojiSheet = false },
                onEmojiSelected = { emoji ->
                    viewModel.onMessageInputChange(uiState.messageInput + emoji)
                },
                onBackspace = {
                    if (uiState.messageInput.isNotEmpty()) {
                        viewModel.onMessageInputChange(uiState.messageInput.dropLast(1))
                    }
                }
            )
        }

        // Attachment Selection Bottom Sheet
        if (showAttachmentSheet) {
            AttachmentPickerBottomSheet(
                onDismiss = { showAttachmentSheet = false },
                onCameraClick = {
                    galleryLauncher.launch("image/*")
                },
                onGalleryClick = {
                    galleryLauncher.launch("image/*")
                },
                onDocumentClick = {
                    documentLauncher.launch("*/*")
                },
                onAudioClick = {
                    documentLauncher.launch("audio/*")
                },
                onLocationClick = {
                    viewModel.onMessageInputChange(uiState.messageInput + " 📍 [Location Shared] ")
                    Toast.makeText(context, "Location attached", Toast.LENGTH_SHORT).show()
                },
                onContactClick = {
                    viewModel.onMessageInputChange(uiState.messageInput + " 👤 [Contact Shared] ")
                    Toast.makeText(context, "Contact attached", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
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
private fun ModernMessageBubble(
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
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isOutgoing) 18.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 18.dp
            ),
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
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
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            text = "edited",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = textColor.copy(alpha = 0.55f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.65f)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerBottomSheet(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val categories = listOf(
        "😀" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛",
            "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒",
            "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢",
            "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨",
            "😰", "😥", "😓", "🫣", "🤗", "🫡", "🤔", "🫢", "🤫", "🤥", "😶", "😶‍🌫️"
        ),
        "👍" to listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌", "🤌", "🤏",
            "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️",
            "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "🫶", "👐", "🤲",
            "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "👀"
        ),
        "❤️" to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️",
            "🔥", "✨", "💫", "⭐", "🌟", "⚡", "💥", "💯", "💢", "💨", "🎉", "🎊"
        ),
        "🐶" to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁",
            "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
            "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋"
        ),
        "🍕" to listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒",
            "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🥑", "🍔", "🍟", "🍕", "🌭", "🥪",
            "🌮", "🌯", "🫔", "🥙", "🧆", "🍜", "🍝", "🍣", "🍱", "🥟", "🍦", "🎂", "☕"
        ),
        "🚀" to listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚",
            "🚛", "🚜", "🛵", "🏍️", "🛺", "🚲", "🛴", "✈️", "🛫", "🛬", "🚀", "🛸", "🚁"
        )
    )

    var selectedTab by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // Top Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEachIndexed { index, (icon, _) ->
                        Surface(
                            onClick = { selectedTab = index },
                            shape = CircleShape,
                            color = if (selectedTab == index) EmeraldPrimary.copy(alpha = 0.18f) else Color.Transparent,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = icon,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }

                // Backspace button
                IconButton(
                    onClick = onBackspace,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Emoji Grid
            val currentEmojis = categories[selectedTab].second
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(currentEmojis.chunked(7)) { rowEmojis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowEmojis.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { onEmojiSelected(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerBottomSheet(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDocumentClick: () -> Unit,
    onAudioClick: () -> Unit,
    onLocationClick: () -> Unit,
    onContactClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Share Content",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AttachmentOptionItem(
                    title = "Camera",
                    icon = Icons.Default.CameraAlt,
                    backgroundColor = Color(0xFFE91E63),
                    onClick = {
                        onDismiss()
                        onCameraClick()
                    }
                )
                AttachmentOptionItem(
                    title = "Gallery",
                    icon = Icons.Default.Image,
                    backgroundColor = Color(0xFF9C27B0),
                    onClick = {
                        onDismiss()
                        onGalleryClick()
                    }
                )
                AttachmentOptionItem(
                    title = "Document",
                    icon = Icons.Default.Description,
                    backgroundColor = Color(0xFF3F51B5),
                    onClick = {
                        onDismiss()
                        onDocumentClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AttachmentOptionItem(
                    title = "Audio",
                    icon = Icons.Default.Audiotrack,
                    backgroundColor = Color(0xFFFF9800),
                    onClick = {
                        onDismiss()
                        onAudioClick()
                    }
                )
                AttachmentOptionItem(
                    title = "Location",
                    icon = Icons.Default.LocationOn,
                    backgroundColor = Color(0xFF4CAF50),
                    onClick = {
                        onDismiss()
                        onLocationClick()
                    }
                )
                AttachmentOptionItem(
                    title = "Contact",
                    icon = Icons.Default.AccountCircle,
                    backgroundColor = Color(0xFF00BCD4),
                    onClick = {
                        onDismiss()
                        onContactClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun AttachmentOptionItem(
    title: String,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = backgroundColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
