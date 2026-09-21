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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Animated Bookshelf Wallpaper for OMA-CHAT
 * Ultra-smooth, 60/120fps hardware-accelerated reproduction of wallpaper/bookshelf/wallpaper.css
 * Features:
 * - Pre-computed zero-allocation shelf layout
 * - Feather-soft realistic radial flashlight illumination falloff
 * - Accurate 20s atmospheric keyframe timeline with eyes encounter & flickers
 */
@Composable
fun BookshelfWallpaper(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bookshelf_anim")

    // Flashlight sweep progress (0.0 to 1.0) along 20s timeline
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flashlight_progress"
    )

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Precalculate bookshelf layout only when container dimensions change
    val bookshelfLayout = remember(canvasSize.width, canvasSize.height) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            generateBookshelfLayout(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .onSizeChanged { canvasSize = it }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val layout = bookshelfLayout ?: return@Canvas

            // 1. Calculate Flashlight position & opacity from keyframes
            val light = calculateSmoothFlashlight(progress, width, height)
            val lightCenter = Offset(light.x, light.y)
            val lightRadiusSq = light.radius * light.radius
            val isLightVisible = light.opacity > 0.01f

            // 2. Draw Shelves and Books with smooth distance-based illumination
            for (shelf in layout.shelves) {
                // Shelf plank base
                val plankColor = if (isLightVisible) {
                    val plankDist = kotlin.math.abs(light.y - shelf.plankTop)
                    val plankIllum = ((1f - (plankDist / light.radius)).coerceIn(0f, 1f)) * light.opacity
                    lerp(shelf.darkPlankColor, shelf.brightPlankColor, plankIllum)
                } else {
                    shelf.darkPlankColor
                }

                drawRect(
                    color = plankColor,
                    topLeft = Offset(0f, shelf.plankTop),
                    size = Size(width, shelf.plankHeight)
                )

                // Books on this shelf
                for (book in shelf.books) {
                    val bookIllumination = if (isLightVisible) {
                        val bookCenterX = book.x + book.width * 0.5f
                        val bookCenterY = book.y + book.height * 0.5f
                        val dx = light.x - bookCenterX
                        val dy = light.y - bookCenterY
                        val distSq = dx * dx + dy * dy

                        if (distSq < lightRadiusSq) {
                            val dist = kotlin.math.sqrt(distSq)
                            val normDist = dist / light.radius
                            // Smooth cosine falloff for soft, realistic ambient lighting
                            val factor = (1f + kotlin.math.cos(normDist * Math.PI.toFloat())) * 0.5f
                            factor * light.opacity
                        } else 0f
                    } else 0f

                    // Interpolate between dark room color and rich vibrant color
                    val renderColor = if (bookIllumination > 0.01f) {
                        lerp(book.darkColor, book.vibrantColor, bookIllumination)
                    } else {
                        book.darkColor
                    }

                    drawRect(
                        color = renderColor,
                        topLeft = Offset(book.x, book.y),
                        size = Size(book.width, book.height)
                    )

                    // Book spine gold ribbing when illuminated
                    if (bookIllumination > 0.15f && book.hasGoldRibbing) {
                        val ribColor = Color(0xFFFFD700).copy(alpha = 0.5f * bookIllumination)
                        drawRect(
                            color = ribColor,
                            topLeft = Offset(book.x + 2f, book.y + 6f),
                            size = Size(book.width - 4f, 2f)
                        )
                        drawRect(
                            color = ribColor,
                            topLeft = Offset(book.x + 2f, book.y + book.height - 10f),
                            size = Size(book.width - 4f, 2f)
                        )
                    }
                }
            }

            // 3. Render Smooth Radial Ambient Flashlight Glow Overlay
            if (isLightVisible) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color(0x38FFF7E0).copy(alpha = 0.28f * light.opacity),
                        0.4f to Color(0x22FFEBB8).copy(alpha = 0.18f * light.opacity),
                        0.75f to Color(0x0CFFD280).copy(alpha = 0.08f * light.opacity),
                        1.0f to Color.Transparent,
                        center = lightCenter,
                        radius = light.radius
                    ),
                    radius = light.radius,
                    center = lightCenter,
                    blendMode = BlendMode.Screen
                )
            }

            // 4. Mysterious Glowing Eyes (Timeline synchronized with CSS animation)
            val eyes = calculateEyesState(progress, width, height)
            if (eyes.alpha > 0.01f) {
                // Left Eye
                drawCircle(
                    color = eyes.color,
                    radius = eyes.radius,
                    center = Offset(eyes.x - 7.5f, eyes.y),
                    alpha = eyes.alpha
                )
                // Right Eye
                drawCircle(
                    color = eyes.color,
                    radius = eyes.radius,
                    center = Offset(eyes.x + 7.5f, eyes.y),
                    alpha = eyes.alpha
                )

                // Soft glow halo around eyes
                if (eyes.hasGlow) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                eyes.color.copy(alpha = 0.35f * eyes.alpha),
                                Color.Transparent
                            ),
                            center = Offset(eyes.x, eyes.y),
                            radius = 24f
                        ),
                        radius = 24f,
                        center = Offset(eyes.x, eyes.y),
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }
    }
}

private data class CachedBook(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val darkColor: Color,
    val vibrantColor: Color,
    val hasGoldRibbing: Boolean
)

private data class CachedShelf(
    val plankTop: Float,
    val plankHeight: Float,
    val darkPlankColor: Color,
    val brightPlankColor: Color,
    val books: List<CachedBook>
)

private data class BookshelfLayout(
    val shelves: List<CachedShelf>
)

private fun generateBookshelfLayout(width: Float, height: Float): BookshelfLayout {
    val numShelves = 5
    val shelfSpacing = height / numShelves
    val plankHeight = 8f

    val darkShelf = Color(0xFF1E1E1E)
    val brightShelf = Color(0xFF4A3420)

    val darkBook1 = Color(0xFF232323)
    val darkBook2 = Color(0xFF2C2C2C)
    val darkBook3 = Color(0xFF1B1B1B)

    // Palette matching wallpaper.css specifications
    val vibrantPalette = listOf(
        Color(0xFFB22222), // Firebrick Red
        Color(0xFF556B2F), // Dark Olive Green
        Color(0xFFFA8072), // Salmon
        Color(0xFF008080), // Teal
        Color(0xFFBDB76B), // Dark Khaki
        Color(0xFF8B4513), // Saddle Brown
        Color(0xFF2F4F4F), // Dark Slate Gray
        Color(0xFFCD5C5C), // Indian Red
        Color(0xFFA52A2A), // Brown
        Color(0xFFD2B48C), // Tan
        Color(0xFFFF6347), // Tomato Red
        Color(0xFF4F5D6A), // Slate
        Color(0xFFBC8F8F)  // Rosy Brown
    )

    val shelves = mutableListOf<CachedShelf>()

    for (s in 0 until numShelves) {
        val plankTop = (s + 1) * shelfSpacing - plankHeight
        val books = mutableListOf<CachedBook>()

        var currentX = 8f
        var bookIndex = s * 9

        while (currentX < width - 16f) {
            // Deterministic pseudorandom parameters based on index
            val seed = (s * 313 + bookIndex * 17)
            val wRand = ((seed % 15) + 14).toFloat() // 14 to 28 dp width
            val hRatio = 0.58f + ((seed % 28) / 100f) // 58% to 86% of shelf height
            val bHeight = (shelfSpacing - plankHeight - 4f) * hRatio
            val bTop = plankTop - bHeight

            val vColor = vibrantPalette[bookIndex % vibrantPalette.size]
            val dColor = when (bookIndex % 3) {
                0 -> darkBook1
                1 -> darkBook2
                else -> darkBook3
            }

            books.add(
                CachedBook(
                    x = currentX,
                    y = bTop,
                    width = wRand - 1.5f,
                    height = bHeight,
                    darkColor = dColor,
                    vibrantColor = vColor,
                    hasGoldRibbing = (bookIndex % 2 == 0)
                )
            )

            currentX += wRand + 1.5f
            bookIndex++
        }

        shelves.add(
            CachedShelf(
                plankTop = plankTop,
                plankHeight = plankHeight,
                darkPlankColor = darkShelf,
                brightPlankColor = brightShelf,
                books = books
            )
        )
    }

    return BookshelfLayout(shelves)
}

private data class SmoothFlashlight(
    val x: Float,
    val y: Float,
    val radius: Float,
    val opacity: Float
)

private fun calculateSmoothFlashlight(progress: Float, width: Float, height: Float): SmoothFlashlight {
    val radius = width * 0.48f // Generous, soft flashlight radius

    return when {
        // 0% - 38%: Sweeping from top-left across to middle right
        progress < 0.38f -> {
            val t = progress / 0.38f
            val smoothT = androidx.compose.animation.core.FastOutSlowInEasing.transform(t)
            SmoothFlashlight(
                x = androidx.compose.ui.util.lerp(-width * 0.25f, width * 0.60f, smoothT),
                y = androidx.compose.ui.util.lerp(height * 0.12f, height * 0.22f, smoothT),
                radius = radius,
                opacity = 1f
            )
        }
        // 38% - 39%: Smooth snap down towards the mystery eyes
        progress < 0.39f -> {
            val t = (progress - 0.38f) / 0.01f
            SmoothFlashlight(
                x = androidx.compose.ui.util.lerp(width * 0.60f, width * 0.60f, t),
                y = androidx.compose.ui.util.lerp(height * 0.22f, height * 0.76f, t),
                radius = radius,
                opacity = 1f
            )
        }
        // 39% - 42%: Flashlight flickering at the eyes
        progress < 0.42f -> {
            val isOff = (progress in 0.398f..0.403f) || (progress in 0.416f..0.420f)
            SmoothFlashlight(
                x = width * 0.60f,
                y = height * 0.76f,
                radius = radius * 0.95f,
                opacity = if (isOff) 0.0f else 1f
            )
        }
        // 42% - 54%: Suspense darkness
        progress < 0.54f -> {
            SmoothFlashlight(
                x = width * 0.60f,
                y = height * 0.76f,
                radius = radius,
                opacity = 0f
            )
        }
        // 54% - 55%: Flashlight clicks back on
        progress < 0.55f -> {
            val t = (progress - 0.54f) / 0.01f
            SmoothFlashlight(
                x = width * 0.60f,
                y = height * 0.76f,
                radius = radius,
                opacity = t
            )
        }
        // 55% - 59%: Steady at the spot
        progress < 0.59f -> {
            SmoothFlashlight(
                x = width * 0.60f,
                y = height * 0.76f,
                radius = radius,
                opacity = 1f
            )
        }
        // 59% - 64%: Inspecting lower left (45% 78%)
        progress < 0.64f -> {
            val t = (progress - 0.59f) / 0.05f
            SmoothFlashlight(
                x = androidx.compose.ui.util.lerp(width * 0.60f, width * 0.45f, t),
                y = androidx.compose.ui.util.lerp(height * 0.76f, height * 0.78f, t),
                radius = radius,
                opacity = 1f
            )
        }
        // 64% - 68%: Moving to lower right (85% 89%)
        progress < 0.68f -> {
            val t = (progress - 0.64f) / 0.04f
            SmoothFlashlight(
                x = androidx.compose.ui.util.lerp(width * 0.45f, width * 0.85f, t),
                y = androidx.compose.ui.util.lerp(height * 0.78f, height * 0.89f, t),
                radius = radius,
                opacity = 1f
            )
        }
        // 68% - 74%: Glancing back at mystery spot (60% 86%)
        progress < 0.74f -> {
            val t = (progress - 0.68f) / 0.06f
            SmoothFlashlight(
                x = androidx.compose.ui.util.lerp(width * 0.85f, width * 0.60f, t),
                y = androidx.compose.ui.util.lerp(height * 0.89f, height * 0.76f, t),
                radius = radius,
                opacity = 1f
            )
        }
        // 74% - 100%: Smoothly drifting out to bottom-right (150% 50%)
        else -> {
            val t = (progress - 0.74f) / 0.26f
            val smoothT = androidx.compose.animation.core.FastOutSlowInEasing.transform(t)
            SmoothFlashlight(
                x = androidx.compose.ui.util.lerp(width * 0.60f, width * 1.50f, smoothT),
                y = androidx.compose.ui.util.lerp(height * 0.76f, height * 0.50f, smoothT),
                radius = radius,
                opacity = 1f
            )
        }
    }
}

private data class EyesState(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val color: Color,
    val hasGlow: Boolean
)

private fun calculateEyesState(progress: Float, width: Float, height: Float): EyesState {
    val eyeX = width * 0.60f
    val eyeY = height * 0.76f

    return when {
        // 38% - 39%: Eyes appear glowing white
        progress in 0.38f..0.398f -> {
            EyesState(
                x = eyeX,
                y = eyeY,
                radius = 3.5f,
                alpha = 1f,
                color = Color.White,
                hasGlow = false
            )
        }
        // 39.8% - 40.5%: Blinking
        progress in 0.398f..0.405f -> {
            EyesState(
                x = eyeX,
                y = eyeY,
                radius = 1.0f,
                alpha = 0f,
                color = Color.White,
                hasGlow = false
            )
        }
        // 40.5% - 42%: Eyes snap open with glowing crimson red
        progress in 0.405f..0.422f -> {
            EyesState(
                x = eyeX,
                y = eyeY,
                radius = 3.5f,
                alpha = 1f,
                color = Color(0xFFFF2222),
                hasGlow = true
            )
        }
        else -> {
            EyesState(
                x = eyeX,
                y = eyeY,
                radius = 3.5f,
                alpha = 0f,
                color = Color.Transparent,
                hasGlow = false
            )
        }
    }
}
