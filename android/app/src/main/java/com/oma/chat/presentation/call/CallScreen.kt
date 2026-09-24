package com.oma.chat.presentation.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.oma.chat.domain.model.CallState
import com.oma.chat.domain.model.CallType
import com.oma.chat.presentation.theme.EmeraldPrimary
import com.oma.chat.presentation.theme.ErrorRed
import java.util.Locale

@Composable
fun CallScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val callState by viewModel.callState.collectAsState()

    // Permissions check
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handled internally
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(callState) {
        if (callState is CallState.Idle) {
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        when (val state = callState) {
            is CallState.OutgoingRinging -> {
                RingingView(
                    name = state.targetName,
                    avatar = state.targetAvatar,
                    statusText = "Ringing...",
                    isSpeakerOn = state.isSpeakerOn,
                    onToggleSpeaker = { viewModel.toggleSpeaker(!state.isSpeakerOn) },
                    onEndCall = { viewModel.endCall() }
                )
            }
            is CallState.Connecting -> {
                RingingView(
                    name = state.targetName,
                    avatar = state.targetAvatar,
                    statusText = "Connecting...",
                    isSpeakerOn = state.isSpeakerOn,
                    onToggleSpeaker = { viewModel.toggleSpeaker(!state.isSpeakerOn) },
                    onEndCall = { viewModel.endCall() }
                )
            }
            is CallState.Connected -> {
                if (state.callType == CallType.VIDEO) {
                    VideoCallView(
                        state = state,
                        viewModel = viewModel
                    )
                } else {
                    VoiceCallView(
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
            is CallState.Ended -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = ErrorRed.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = state.reason,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun RingingView(
    name: String,
    avatar: String,
    statusText: String,
    isSpeakerOn: Boolean = false,
    onToggleSpeaker: (() -> Unit)? = null,
    onEndCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.2f))
            )
            if (avatar.isNotBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = name,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color.White
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onToggleSpeaker != null) {
                IconButton(
                    onClick = onToggleSpeaker,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isSpeakerOn) EmeraldPrimary else Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker"
                    )
                }
                Spacer(modifier = Modifier.width(32.dp))
            }

            FloatingActionButton(
                onClick = onEndCall,
                containerColor = ErrorRed,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(68.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceCallView(
    state: CallState.Connected,
    viewModel: CallViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Text(
                text = state.targetName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatDuration(state.durationSeconds),
                style = MaterialTheme.typography.titleMedium,
                color = EmeraldPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(EmeraldPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (state.targetAvatar.isNotBlank()) {
                AsyncImage(
                    model = state.targetAvatar,
                    contentDescription = state.targetName,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = EmeraldPrimary
                )
            }
        }

        CallControlsBar(
            isAudioMuted = state.isAudioMuted,
            isVideoMuted = state.isVideoMuted,
            isSpeakerOn = state.isSpeakerOn,
            isVideoCall = false,
            onToggleMute = { viewModel.toggleMute(!state.isAudioMuted) },
            onToggleVideo = { viewModel.toggleVideo(!state.isVideoMuted) },
            onSwitchCamera = { viewModel.switchCamera() },
            onToggleSpeaker = { viewModel.toggleSpeaker(!state.isSpeakerOn) },
            onEndCall = { viewModel.endCall() }
        )
    }
}

@Composable
private fun VideoCallView(
    state: CallState.Connected,
    viewModel: CallViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Remote Fullscreen Video
        val remoteVideoTrack = viewModel.webRtcClient.remoteVideoTrack
        if (remoteVideoTrack != null) {
            WebRtcSurfaceView(
                videoTrack = remoteVideoTrack,
                eglBaseContext = viewModel.webRtcClient.eglBase.eglBaseContext,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${state.targetName}'s camera is off",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Local Pip Video (Top Right)
        val localVideoTrack = viewModel.webRtcClient.localVideoTrack
        if (localVideoTrack != null && !state.isVideoMuted) {
            Box(
                modifier = Modifier
                    .padding(top = 48.dp, end = 20.dp)
                    .size(width = 110.dp, height = 155.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .align(Alignment.TopEnd)
            ) {
                WebRtcSurfaceView(
                    videoTrack = localVideoTrack,
                    eglBaseContext = viewModel.webRtcClient.eglBase.eglBaseContext,
                    isMirror = state.isFrontCamera,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top info header
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 20.dp)
        ) {
            Text(
                text = state.targetName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = formatDuration(state.durationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = EmeraldPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bottom Controls Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            CallControlsBar(
                isAudioMuted = state.isAudioMuted,
                isVideoMuted = state.isVideoMuted,
                isSpeakerOn = state.isSpeakerOn,
                isVideoCall = true,
                onToggleMute = { viewModel.toggleMute(!state.isAudioMuted) },
                onToggleVideo = { viewModel.toggleVideo(!state.isVideoMuted) },
                onSwitchCamera = { viewModel.switchCamera() },
                onToggleSpeaker = { viewModel.toggleSpeaker(!state.isSpeakerOn) },
                onEndCall = { viewModel.endCall() }
            )
        }
    }
}

@Composable
private fun CallControlsBar(
    isAudioMuted: Boolean,
    isVideoMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoCall: Boolean,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.85f),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button
            IconButton(
                onClick = onToggleMute,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isAudioMuted) Color.White else Color.White.copy(alpha = 0.15f),
                    contentColor = if (isAudioMuted) Color.Black else Color.White
                ),
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    imageVector = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute"
                )
            }

            // Video Toggle Button (for video calls)
            if (isVideoCall) {
                IconButton(
                    onClick = onToggleVideo,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isVideoMuted) Color.White else Color.White.copy(alpha = 0.15f),
                        contentColor = if (isVideoMuted) Color.Black else Color.White
                    ),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        imageVector = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = "Video"
                    )
                }

                // Camera Switch Button
                IconButton(
                    onClick = onSwitchCamera,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera"
                    )
                }
            }

            // Speaker Button
            IconButton(
                onClick = onToggleSpeaker,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSpeakerOn) EmeraldPrimary else Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    contentDescription = "Speaker"
                )
            }

            // End Call Button
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = ErrorRed,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(54.dp),
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
