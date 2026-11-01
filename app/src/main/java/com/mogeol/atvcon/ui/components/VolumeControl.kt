package com.mogeol.atvcon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
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
import com.mogeol.atvcon.data.TvKeys
import com.mogeol.atvcon.ui.theme.AccentCyan
import com.mogeol.atvcon.ui.theme.DarkSurfaceVariant
import com.mogeol.atvcon.ui.theme.TextSecondary

@Composable
fun VolumeControl(
    currentVolume: Int,
    maxVolume: Int = 15,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onKeyClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(currentVolume) { mutableFloatStateOf(currentVolume.toFloat()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TV Audio Level",
                    color = TextSecondary,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${sliderValue.toInt()} / $maxVolume",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        onVolumeChange(sliderValue.toInt())
                    },
                    valueRange = 0f..maxVolume.toFloat(),
                    steps = maxVolume - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(0, 3, 7, 11, 15).forEach { level ->
                        OutlinedButton(
                            onClick = {
                                sliderValue = level.toFloat()
                                onVolumeChange(level)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(text = "$level", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Dual Vertical Rockers: CH on Left, Mute Center, VOL on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Vertical CH Rocker
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier
                    .width(64.dp)
                    .height(138.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = { onKeyClick(TvKeys.CHANNEL_UP) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "CH +",
                            tint = AccentCyan
                        )
                    }
                    Text(
                        text = stringResource(R.string.nav_ch),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    IconButton(
                        onClick = { onKeyClick(TvKeys.CHANNEL_DOWN) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "CH -",
                            tint = AccentCyan
                        )
                    }
                }
            }

            // Center: Mute Toggle Button
            FilledTonalIconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(Icons.Default.VolumeMute, contentDescription = "Mute", tint = Color.White)
            }

            // Right: Vertical VOL Rocker
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier
                    .width(64.dp)
                    .height(138.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = { onKeyClick(TvKeys.VOLUME_UP) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Vol +",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.nav_vol),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    IconButton(
                        onClick = { onKeyClick(TvKeys.VOLUME_DOWN) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Vol -",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

