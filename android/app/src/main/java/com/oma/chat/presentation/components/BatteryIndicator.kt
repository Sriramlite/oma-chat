package com.oma.chat.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BatteryGreen = Color(0xFF10B981)
val BatteryAmber = Color(0xFFF59E0B)

@Composable
fun BatteryIcon(
    level: Int,
    color: Color = BatteryGreen,
    modifier: Modifier = Modifier,
    width: Dp = 18.dp,
    height: Dp = 10.dp
) {
    Canvas(modifier = modifier.size(width, height)) {
        val strokeWidth = 1.3.dp.toPx()
        val cornerRadius = CornerRadius(2.dp.toPx())
        val tipWidth = 2.dp.toPx()
        val tipHeight = size.height * 0.45f
        val bodyWidth = size.width - tipWidth - 1.dp.toPx()
        val bodyHeight = size.height

        // Outer body rounded rectangle outline
        drawRoundRect(
            color = color,
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(bodyWidth - strokeWidth, bodyHeight - strokeWidth),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )

        // Positive terminal tip on the right
        val tipTop = (bodyHeight - tipHeight) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyWidth, tipTop),
            size = Size(tipWidth, tipHeight),
            cornerRadius = CornerRadius(1.dp.toPx())
        )

        // Proportional inner battery fill
        val clampedLevel = (level / 100f).coerceIn(0.06f, 1f)
        val innerPadding = strokeWidth * 1.5f
        val maxFillWidth = bodyWidth - (innerPadding * 2)
        val fillWidth = maxFillWidth * clampedLevel
        val fillHeight = bodyHeight - (innerPadding * 2)

        if (fillWidth > 0) {
            drawRoundRect(
                color = color,
                topLeft = Offset(innerPadding, innerPadding),
                size = Size(fillWidth, fillHeight),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

@Composable
fun ChargingBoltIcon(
    modifier: Modifier = Modifier,
    color: Color = BatteryAmber,
    size: Dp = 12.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.58f, 0f)
            lineTo(w * 0.12f, h * 0.56f)
            lineTo(w * 0.50f, h * 0.56f)
            lineTo(w * 0.38f, h * 1f)
            lineTo(w * 0.88f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.44f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
fun BatteryStatusBadge(
    level: Int,
    isCharging: Boolean = false,
    modifier: Modifier = Modifier,
    textColor: Color = BatteryGreen,
    fontSize: TextUnit = 12.sp,
    iconWidth: Dp = 18.dp,
    iconHeight: Dp = 10.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BatteryIcon(
            level = level,
            color = textColor,
            width = iconWidth,
            height = iconHeight
        )
        Text(
            text = "$level%",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        )
        if (isCharging) {
            ChargingBoltIcon(
                size = (fontSize.value * 0.95).dp
            )
        }
    }
}
