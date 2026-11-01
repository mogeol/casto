package com.mogeol.atvcon.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogeol.atvcon.R
import com.mogeol.atvcon.ui.theme.AccentCyan
import com.mogeol.atvcon.ui.theme.DarkSurfaceVariant
import com.mogeol.atvcon.ui.theme.TextSecondary

@Composable
fun ScrollPad(
    onPageScroll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 114.dp,
    height: Dp = 224.dp
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scrollThresholdPx = with(density) { 26.dp.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
    var indicatorOffsetRatio by remember { mutableFloatStateOf(0f) }

    val animatedIndicatorRatio by animateFloatAsState(
        targetValue = if (isDragging) indicatorOffsetRatio else 0f,
        label = "scrollIndicator"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E2129),
        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.4f)),
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val isDown = offset.y > size.height / 2
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPageScroll(isDown)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragAccumulatedY = 0f
                        indicatorOffsetRatio = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        dragAccumulatedY = 0f
                        indicatorOffsetRatio = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragAccumulatedY = 0f
                        indicatorOffsetRatio = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatedY += dragAmount.y
                        indicatorOffsetRatio = (indicatorOffsetRatio + dragAmount.y / 120f).coerceIn(-1f, 1f)

                        if (dragAccumulatedY <= -scrollThresholdPx) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPageScroll(false)
                            dragAccumulatedY += scrollThresholdPx
                        } else if (dragAccumulatedY >= scrollThresholdPx) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPageScroll(true)
                            dragAccumulatedY -= scrollThresholdPx
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Direction Indicators (Top & Bottom subtle chevrons)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = if (animatedIndicatorRatio < -0.15f) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (animatedIndicatorRatio > 0.15f) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Center Scroll Strip (Textured Ribbed Track + Label)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Top Ribbed Groove
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .background(
                            if (animatedIndicatorRatio < -0.15f) AccentCyan else Color.DarkGray.copy(alpha = 0.6f),
                            RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scroll Track Indicator Pill (moves with finger)
                Box(
                    modifier = Modifier
                        .offset(y = (animatedIndicatorRatio * 20).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDragging) DarkSurfaceVariant else Color.Transparent
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.nav_scroll),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDragging) AccentCyan else TextSecondary,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Ribbed Groove
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .background(
                            if (animatedIndicatorRatio > 0.15f) AccentCyan else Color.DarkGray.copy(alpha = 0.6f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
