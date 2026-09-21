package com.oma.chat.presentation.common.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.oma.chat.presentation.theme.EmeraldPrimary

/**
 * Universal Avatar Image Component for OMA-CHAT
 * Reliably loads:
 * 1. Base64 data URIs (`data:image/jpeg;base64,...`) and raw Base64 strings
 * 2. Backend relative URLs (`/uploads/...`)
 * 3. Full HTTP/HTTPS URLs
 * 4. Fallback initials or default avatar icon
 */
@Composable
fun AvatarImage(
    avatar: String?,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    val trimmed = avatar?.trim().orEmpty()
    val displayName = name?.trim().takeIf { !it.isNullOrBlank() } ?: "User"

    // 1. Decode Base64 avatar if present
    val base64Bitmap = remember(trimmed) {
        if (trimmed.startsWith("data:image") || (trimmed.length > 100 && !trimmed.startsWith("http") && !trimmed.startsWith("/"))) {
            try {
                val cleanBase64 = if (trimmed.contains(",")) {
                    trimmed.substringAfter(",")
                } else {
                    trimmed
                }
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(EmeraldPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (base64Bitmap != null) {
            Image(
                bitmap = base64Bitmap.asImageBitmap(),
                contentDescription = displayName,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (trimmed.isNotBlank()) {
            val resolvedUrl = if (trimmed.startsWith("/")) {
                "https://api.pdktdev.in$trimmed"
            } else {
                trimmed
            }

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolvedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = displayName,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                error = {
                    FallbackAvatar(name = displayName, size = size)
                }
            )
        } else {
            FallbackAvatar(name = displayName, size = size)
        }
    }
}

@Composable
private fun FallbackAvatar(
    name: String,
    size: Dp
) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: ""
    if (initial.isNotBlank() && size >= 32.dp) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = EmeraldPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
    } else {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = name,
            tint = EmeraldPrimary,
            modifier = Modifier.fillMaxSize(0.6f)
        )
    }
}
