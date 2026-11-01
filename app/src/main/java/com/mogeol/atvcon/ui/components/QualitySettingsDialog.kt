package com.mogeol.atvcon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogeol.atvcon.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogeol.atvcon.data.MirrorQuality
import com.mogeol.atvcon.ui.theme.*

@Composable
fun QualitySettingsDialog(
    currentQuality: MirrorQuality,
    onApply: (MirrorQuality) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMaxSize by remember { mutableIntStateOf(currentQuality.maxSize) }
    var selectedBitrate by remember { mutableIntStateOf(currentQuality.bitRate) }
    var selectedFps by remember { mutableIntStateOf(currentQuality.maxFps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.scrcpy_quality_settings), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Resolution
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.scrcpy_resolution), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            1920 to "1080p (FHD)",
                            1280 to "720p (HD)",
                            854 to "480p (SD)"
                        ).forEach { (size, label) ->
                            val isSelected = selectedMaxSize == size
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentCyan.copy(alpha = 0.18f) else DarkSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentCyan else Color.DarkGray.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedMaxSize = size }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentCyan else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Video Bitrate
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.scrcpy_bitrate), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            8_000_000 to "8M",
                            4_000_000 to "4M",
                            2_000_000 to "2M",
                            1_000_000 to "1M"
                        ).forEach { (rate, label) ->
                            val isSelected = selectedBitrate == rate
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentCyan.copy(alpha = 0.18f) else DarkSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentCyan else Color.DarkGray.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedBitrate = rate }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentCyan else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Max Framerate
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.scrcpy_framerate), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            60 to "60 FPS (Fluid)",
                            30 to "30 FPS (Battery Saver)"
                        ).forEach { (fps, label) ->
                            val isSelected = selectedFps == fps
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentCyan.copy(alpha = 0.18f) else DarkSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentCyan else Color.DarkGray.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedFps = fps }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentCyan else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Hardware Overlay Notice
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Text(
                            text = "Black screen during video playback? Android TV media players (SmartTube, Netflix, etc.) decode video directly into hardware overlay planes (SurfaceView) or DRM buffers that bypass Android screen capture. In SmartTube or MX Player, switching video output from SurfaceView to TextureView enables live mirror capture.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        MirrorQuality(
                            maxSize = selectedMaxSize,
                            bitRate = selectedBitrate,
                            maxFps = selectedFps
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.scrcpy_apply_reconnect), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(18.dp)
    )
}
