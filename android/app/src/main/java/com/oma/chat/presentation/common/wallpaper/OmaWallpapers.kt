package com.oma.chat.presentation.common.wallpaper

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oma.chat.presentation.theme.EmeraldPrimary
import kotlin.math.cos
import kotlin.math.sin

data class WallpaperItem(
    val id: String,
    val name: String,
    val description: String,
    val previewGradient: List<Color>
)

object WallpaperRegistry {
    val wallpapers = listOf(
        WallpaperItem(
            id = "bookshelf",
            name = "Bookshelf Library",
            description = "Atmospheric illuminated library shelf",
            previewGradient = listOf(Color(0xFF2C1810), Color(0xFF140D07))
        ),
        WallpaperItem(
            id = "chase",
            name = "Cyber Matrix",
            description = "Futuristic cybernetic streaming matrix",
            previewGradient = listOf(Color(0xFF021B14), Color(0xFF000806))
        ),
        WallpaperItem(
            id = "emerald",
            name = "Emerald Luxe",
            description = "Signature OMA WhatsApp-style luxury green",
            previewGradient = listOf(Color(0xFF0B241E), Color(0xFF05120F))
        ),
        WallpaperItem(
            id = "sunset",
            name = "Sunset Aurora",
            description = "Deep violet & twilight amber warmth",
            previewGradient = listOf(Color(0xFF2E1065), Color(0xFF4C0519), Color(0xFF0C0A09))
        ),
        WallpaperItem(
            id = "space",
            name = "Cosmic Void",
            description = "Twinkling starfield on pitch-black OLED",
            previewGradient = listOf(Color(0xFF090D16), Color(0xFF020408))
        ),
        WallpaperItem(
            id = "dots",
            name = "Geometric Minimal",
            description = "Subtle architectural dotted grid",
            previewGradient = listOf(Color(0xFF18181B), Color(0xFF09090B))
        ),
        WallpaperItem(
            id = "midnight",
            name = "Midnight Slate",
            description = "Ultra-clean distraction-free dark canvas",
            previewGradient = listOf(Color(0xFF121417), Color(0xFF08090A))
        )
    )
}

@Composable
fun OmaWallpaperHost(
    wallpaperId: String,
    modifier: Modifier = Modifier
) {
    when (wallpaperId.lowercase()) {
        "bookshelf" -> BookshelfWallpaper(modifier = modifier)
        "chase" -> ChaseWallpaper(modifier = modifier)
        "emerald" -> EmeraldLuxeWallpaper(modifier = modifier)
        "sunset" -> SunsetAuroraWallpaper(modifier = modifier)
        "space" -> CosmicVoidWallpaper(modifier = modifier)
        "dots" -> GeometricDotsWallpaper(modifier = modifier)
        "midnight" -> MidnightSlateWallpaper(modifier = modifier)
        else -> BookshelfWallpaper(modifier = modifier)
    }
}

@Composable
fun EmeraldLuxeWallpaper(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "emerald_luxe")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.4f)

        drawRect(color = Color(0xFF061410))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    EmeraldPrimary.copy(alpha = 0.18f * pulse),
                    Color(0xFF0E382B).copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = center,
                radius = w * 0.9f * pulse
            ),
            center = center,
            radius = w * 0.9f * pulse
        )
    }
}

@Composable
fun SunsetAuroraWallpaper(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sunset")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF240E48),
                        Color(0xFF4A1525),
                        Color(0xFF1E1015),
                        Color(0xFF090608)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * (0.3f + shift * 0.4f), size.height * 0.55f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF7A00).copy(alpha = 0.14f),
                        Color(0xFFE11D48).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width * 0.85f
                ),
                center = center,
                radius = size.width * 0.85f
            )
        }
    }
}

@Composable
fun CosmicVoidWallpaper(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cosmic")
    val twinkle by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF05070A))

        // Ambient Nebula
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1E1B4B).copy(alpha = 0.25f),
                    Color(0xFF0F172A).copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.8f, size.height * 0.2f),
                radius = size.width * 0.9f
            ),
            center = Offset(size.width * 0.8f, size.height * 0.2f),
            radius = size.width * 0.9f
        )

        // Deterministic Stars
        val seed = 42
        for (i in 0 until 60) {
            val sx = ((i * 137.5f) % size.width)
            val sy = ((i * 269.3f) % size.height)
            val r = if (i % 5 == 0) 1.8f else 1.1f
            val a = if (i % 2 == 0) 0.8f * twinkle else 0.5f * (1.4f - twinkle)
            drawCircle(
                color = Color.White.copy(alpha = a.coerceIn(0.1f, 0.95f)),
                center = Offset(sx, sy),
                radius = r
            )
        }
    }
}

@Composable
fun GeometricDotsWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF111315))

        val step = 28.dp.toPx()
        val cols = (size.width / step).toInt() + 1
        val rows = (size.height / step).toInt() + 1

        for (r in 0..rows) {
            for (c in 0..cols) {
                val cx = c * step
                val cy = r * step
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    center = Offset(cx, cy),
                    radius = 1.4.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun MidnightSlateWallpaper(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1D21),
                        Color(0xFF0E1012),
                        Color(0xFF070809)
                    )
                )
            )
    )
}
