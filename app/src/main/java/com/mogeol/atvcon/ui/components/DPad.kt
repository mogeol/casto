package com.mogeol.atvcon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogeol.atvcon.data.TvKeys
import com.mogeol.atvcon.ui.theme.AccentCyan
import com.mogeol.atvcon.ui.theme.ButtonBackground
import com.mogeol.atvcon.ui.theme.DarkSurfaceVariant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DPad(
    onKeyClick: (Int) -> Unit,
    onKeyLongClick: ((Int) -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onPlayClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 312.dp
) {
    val haptic = LocalHapticFeedback.current
    val cornerRadius = 24.dp
    val bottomExtension = 38.dp
    val totalHeight = size + bottomExtension
    val armSize = size * 0.33f
    val armWidth = size * 0.42f
    val centerOkSize = size * 0.38f

    // Unified Outer Container: Circular Arch at Top, Flat with Rounded Corners at Bottom
    Surface(
        shape = RoundedCornerShape(
            topStart = size / 2,
            topEnd = size / 2,
            bottomStart = cornerRadius,
            bottomEnd = cornerRadius
        ),
        color = Color(0xFF1E2129),
        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.4f)),
        modifier = modifier.size(width = size, height = totalHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // --- Lower-Left Flank: Back Button ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(width = size / 2, height = 135.dp)
                    .clip(RoundedCornerShape(bottomStart = cornerRadius))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true, color = AccentCyan)
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (onBackClick != null) onBackClick() else onKeyClick(TvKeys.BACK)
                    },
                contentAlignment = Alignment.BottomStart
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(start = 28.dp, bottom = 14.dp)
                        .size(28.dp)
                )
            }

            // --- Lower-Right Flank: Play/Pause Button ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(width = size / 2, height = 135.dp)
                    .clip(RoundedCornerShape(bottomEnd = cornerRadius))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true, color = AccentCyan)
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (onPlayClick != null) onPlayClick() else onKeyClick(TvKeys.MEDIA_PLAY_PAUSE)
                    },
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(end = 28.dp, bottom = 14.dp)
                        .size(30.dp)
                )
            }

            // Vertical Center Divider between Back and Play at the bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(1.dp)
                    .height(bottomExtension)
                    .background(Color.DarkGray.copy(alpha = 0.45f))
            )

            // --- Circular D-Pad Disc (Up, Down, Left, Right, Center OK) ---
            Surface(
                shape = CircleShape,
                color = DarkSurfaceVariant,
                border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.45f)),
                modifier = Modifier
                    .size(size)
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // UP Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(armWidth)
                            .height(armSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true, color = AccentCyan)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onKeyClick(TvKeys.UP)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Up",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // DOWN Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .width(armWidth)
                            .height(armSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true, color = AccentCyan)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onKeyClick(TvKeys.DOWN)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Down",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // LEFT Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .height(armWidth)
                            .width(armSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true, color = AccentCyan)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onKeyClick(TvKeys.LEFT)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Left",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // RIGHT Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .height(armWidth)
                            .width(armSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true, color = AccentCyan)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onKeyClick(TvKeys.RIGHT)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Right",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // CENTER OK Button (Tap and Long-Press)
                    Box(
                        modifier = Modifier
                            .size(centerOkSize)
                            .clip(CircleShape)
                            .background(ButtonBackground)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true, color = AccentCyan),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onKeyClick(TvKeys.OK)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (onKeyLongClick != null) {
                                        onKeyLongClick(TvKeys.OK)
                                    } else {
                                        onKeyClick(TvKeys.OK)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OK",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                }
            }
        }
    }
}
