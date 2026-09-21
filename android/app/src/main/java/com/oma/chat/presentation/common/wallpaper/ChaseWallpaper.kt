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
import kotlin.random.Random

/**
 * Chase (Molten Ember) Wallpaper for OMA-CHAT
 * Faithful native Jetpack Compose reproduction of wallpaper/chase/wallpaper.css
 * Features:
 * - Deep dark molten radial gradient from bottom (deep amber/burnt orange to pitch black)
 * - Drifting glowing ember particles with subtle heat wave pulses
 */
@Composable
fun ChaseWallpaper(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chase_anim")

    val emberProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ember_drift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0502))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Molten radial gradient from bottom center
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF5C2304), // Warm glowing ember core
                        Color(0xFF301102), // Dark molten brown
                        Color(0xFF150701), // Pitch black vignette
                        Color(0xFF080200)
                    ),
                    center = Offset(width * 0.5f, height * 1.05f),
                    radius = height * 0.85f
                ),
                size = size
            )

            // 2. Floating floating glowing embers
            val random = Random(42)
            val numEmbers = 40

            for (i in 0 until numEmbers) {
                val startX = random.nextFloat() * width
                val speed = 0.5f + random.nextFloat() * 0.8f
                val sizeEmber = 2f + random.nextFloat() * 4f
                val driftX = kotlin.math.sin((emberProgress * 6.28f * speed) + i) * 20f

                val currentY = (height * 1.1f) - ((emberProgress * speed * height * 1.2f + (i * (height / numEmbers))) % (height * 1.2f))
                val currentX = startX + driftX

                val alpha = ((1f - (currentY / height)) * 0.8f).coerceIn(0.1f, 0.9f)

                // Glowing aura
                drawCircle(
                    color = Color(0xFFFF6600).copy(alpha = alpha * 0.4f),
                    radius = sizeEmber * 2.2f,
                    center = Offset(currentX, currentY)
                )

                // Ember core
                drawCircle(
                    color = Color(0xFFFFCC33).copy(alpha = alpha),
                    radius = sizeEmber,
                    center = Offset(currentX, currentY)
                )
            }
        }
    }
}
