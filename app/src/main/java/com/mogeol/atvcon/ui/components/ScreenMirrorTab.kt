package com.mogeol.atvcon.ui.components

import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mogeol.atvcon.data.MirrorQuality
import com.mogeol.atvcon.data.MirrorQualityManager
import com.mogeol.atvcon.data.TvKeys
import com.mogeol.atvcon.scrcpy.ScrcpyClient
import com.mogeol.atvcon.scrcpy.ScrcpyProtocol
import com.mogeol.atvcon.scrcpy.ScrcpyState
import com.mogeol.atvcon.ui.theme.*
import dadb.Dadb

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScreenMirrorTab(
    dadb: Dadb?,
    targetIp: String,
    isConnected: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
    onRemoteOrientationChanged: (Boolean) -> Unit = {},
    onSendKey: (Int) -> Unit,
    onChannelNext: (Boolean) -> Unit,
    onOpenKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scrcpyClient = remember { ScrcpyClient() }

    val mirrorState by scrcpyClient.state.collectAsState()

    // Quality preferences
    val qualityManager = remember { MirrorQualityManager(context) }
    var currentQuality by remember { mutableStateOf(qualityManager.quality) }
    var showQualityDialog by remember { mutableStateOf(false) }

    // Double tap timing
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // Active output surface
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    // Intercept back button when in fullscreen to collapse to scaled-in mode
    BackHandler(enabled = isFullscreen) {
        onToggleFullscreen(false)
    }

    // Stop scrcpy stream when leaving this tab
    DisposableEffect(Unit) {
        onDispose {
            onToggleFullscreen(false)
            scrcpyClient.stop()
        }
    }

    // Automatically connect, reconnect or reload stream whenever target IP, dadb, surface or quality changes!
    LaunchedEffect(dadb, targetIp, isConnected, currentSurface, currentQuality) {
        val surface = currentSurface
        if (isConnected && dadb != null && surface != null && surface.isValid) {
            scrcpyClient.stop()
            scrcpyClient.start(
                surface = surface,
                dadb = dadb,
                cacheDir = context.cacheDir,
                assets = context.assets,
                maxSize = currentQuality.maxSize,
                bitRate = currentQuality.bitRate,
                maxFps = currentQuality.maxFps,
                scope = scope
            )
        } else if (!isConnected || dadb == null) {
            scrcpyClient.stop()
        }
    }

    if (!isConnected || dadb == null) {
        // Disconnected State Placeholder
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Default.TvOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "TV Not Connected",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Connect to an ADB device ($targetIp) to activate the live interactive screen mirror.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        return
    }

    val connectedState = mirrorState as? ScrcpyState.Connected
    val videoW = (connectedState?.width ?: scrcpyClient.videoWidth).coerceAtLeast(1)
    val videoH = (connectedState?.height ?: scrcpyClient.videoHeight).coerceAtLeast(1)
    val videoAspect = videoW.toFloat() / videoH.toFloat()
    val isRemoteLandscape = videoW >= videoH
    val density = LocalDensity.current

    // Synchronize parent remote orientation state
    LaunchedEffect(isRemoteLandscape) {
        onRemoteOrientationChanged(isRemoteLandscape)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===================================================================
        // 1. VIDEO MIRROR CONTAINER (SINGLE PERSISTENT INSTANCE)
        // Takes all available vertical space above bottom controls (weight 1f)
        // ===================================================================
        BoxWithConstraints(
            modifier = if (isFullscreen) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            } else {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            }
        ) {
            val boxWidthPx = constraints.maxWidth.toFloat()
            val boxHeightPx = constraints.maxHeight.toFloat()

            // Maintain exact aspect ratio fit within box bounds
            val (renderW, renderH) = with(density) {
                val boxAspect = boxWidthPx / boxHeightPx.coerceAtLeast(1f)
                val finalW: Float
                val finalH: Float
                if (boxAspect > videoAspect) {
                    // Box is wider than video -> fit height
                    finalH = boxHeightPx
                    finalW = boxHeightPx * videoAspect
                } else {
                    // Box is taller than video -> fit width
                    finalW = boxWidthPx
                    finalH = boxWidthPx / videoAspect
                }
                finalW.toDp() to finalH.toDp()
            }

            val renderWPx = with(density) { renderW.toPx() }
            val renderHPx = with(density) { renderH.toPx() }

            // Interactive Video Target with 1:1 Touch Mapping
            Box(
                modifier = Modifier
                    .size(renderW, renderH)
                    .align(Alignment.Center)
                .pointerInteropFilter { motionEvent ->
                    if (renderWPx > 0f && renderHPx > 0f) {
                        val tvX = ((motionEvent.x / renderWPx) * videoW).toInt().coerceIn(0, videoW)
                        val tvY = ((motionEvent.y / renderHPx) * videoH).toInt().coerceIn(0, videoH)

                        when (motionEvent.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 320) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleFullscreen(!isFullscreen)
                                    return@pointerInteropFilter true
                                }
                                lastTapTime = now
                                scrcpyClient.sendTouchEvent(ScrcpyProtocol.ACTION_DOWN, tvX, tvY, motionEvent.pressure)
                            }
                            MotionEvent.ACTION_MOVE -> {
                                scrcpyClient.sendTouchEvent(ScrcpyProtocol.ACTION_MOVE, tvX, tvY, motionEvent.pressure)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                scrcpyClient.sendTouchEvent(ScrcpyProtocol.ACTION_UP, tvX, tvY, 0f)
                            }
                        }
                    }
                    true
                }
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                    currentSurface = Surface(st)
                                }

                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                    // Retains active SurfaceTexture during fullscreen layout switch
                                }

                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    currentSurface = null
                                    scrcpyClient.setSurface(null)
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    update = { tv ->
                        if (tv.isAvailable && currentSurface == null) {
                            tv.surfaceTexture?.let { st -> currentSurface = Surface(st) }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top-right Action Pill: [ ⚙ Quality ] and [ ⛶ Fullscreen ] (Scaled-in mode only)
            if (!isFullscreen) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xCC181A20),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { showQualityDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Mirror Quality & Bandwidth",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleFullscreen(true)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Expand Fullscreen",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Floating Navigation Pill (only in fullscreen mode)
            if (isFullscreen) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xDD181A20),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { scrcpyClient.sendKeyCode(TvKeys.BACK) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { scrcpyClient.sendKeyCode(TvKeys.HOME) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { scrcpyClient.sendKeyCode(TvKeys.APP_SWITCH) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.FilterNone, contentDescription = "App Switcher", tint = Color.White, modifier = Modifier.size(19.dp))
                        }

                        IconButton(
                            onClick = { scrcpyClient.sendKeyCode(TvKeys.MENU) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = onOpenKeyboard,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = AccentCyan, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { showQualityDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "Quality Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        VerticalDivider(
                            color = Color.DarkGray.copy(alpha = 0.6f),
                            modifier = Modifier
                                .height(22.dp)
                                .padding(horizontal = 4.dp)
                        )

                        // Collapse back to scaled-in mode
                        IconButton(
                            onClick = { onToggleFullscreen(false) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = AccentCyan, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // Connecting Indicator
            if (mirrorState is ScrcpyState.Connecting) {
                val msg = (mirrorState as ScrcpyState.Connecting).message
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(msg, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // ===================================================================
        // 2. BOTTOM COMPANION CONTROLS (Only when !isFullscreen)
        // Packed comfortably close to the bottom of the screen!
        // ===================================================================
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quick TV Action Toolbar Row
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E2129),
                    border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSendKey(TvKeys.BACK) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onSendKey(TvKeys.HOME) }) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onSendKey(TvKeys.APP_SWITCH) }) {
                            Icon(Icons.Default.FilterNone, contentDescription = "App Switcher", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onSendKey(TvKeys.MENU) }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onOpenKeyboard) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = AccentCyan, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onSendKey(TvKeys.VOLUME_MUTE) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeMute, contentDescription = "Mute", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Companion Controls: [ CH Rocker ] [ Compact D-Pad ] [ VOL Rocker ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Channel / Track Rocker
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .width(58.dp)
                            .height(168.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onChannelNext(true)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Next Track / CH +", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Text("CH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onChannelNext(false)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Prev Track / CH -", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // Center: Compact D-Pad
                    DPad(
                        onKeyClick = onSendKey,
                        size = 196.dp
                    )

                    // Right: Volume Rocker
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .width(58.dp)
                            .height(168.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSendKey(TvKeys.VOLUME_UP)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Vol +", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Text("VOL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSendKey(TvKeys.VOLUME_DOWN)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Vol -", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Mirror Quality & Bandwidth Settings Dialog
    if (showQualityDialog) {
        QualitySettingsDialog(
            currentQuality = currentQuality,
            onApply = { newQuality ->
                qualityManager.quality = newQuality
                currentQuality = newQuality
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }
}
