package com.mogeol.atvcon.scrcpy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mogeol.atvcon.data.TvKeys
import com.mogeol.atvcon.ui.theme.AccentCyan
import com.mogeol.atvcon.ui.theme.DarkSurface
import com.mogeol.atvcon.ui.theme.DarkSurfaceVariant
import dadb.Dadb

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScrcpyMirrorView(
    dadb: Dadb,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val scrcpyClient = remember { ScrcpyClient() }

    val mirrorState by scrcpyClient.state.collectAsState()
    var isLandscape by remember { mutableStateOf(true) }

    // Automatically force Landscape to align with TV screen aspect ratio (16:9)
    LaunchedEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // Ensure orientation smoothly resets back to Portrait on exit
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            scrcpyClient.stop()
        }
    }

    BackHandler {
        onDismiss()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        val connectedState = mirrorState as? ScrcpyState.Connected
        val videoW = (connectedState?.width ?: scrcpyClient.videoWidth).coerceAtLeast(1)
        val videoH = (connectedState?.height ?: scrcpyClient.videoHeight).coerceAtLeast(1)
        val videoAspect = videoW.toFloat() / videoH.toFloat()

        // Calculate exact fitted video box dimensions preserving aspect ratio (0% distortion)
        val (renderWidthDp, renderHeightDp) = with(density) {
            val containerAspect = containerWidthPx / containerHeightPx
            val finalW: Float
            val finalH: Float
            if (containerAspect > videoAspect) {
                // Container wider than video (pillarbox)
                finalH = containerHeightPx
                finalW = containerHeightPx * videoAspect
            } else {
                // Container taller than video (letterbox)
                finalW = containerWidthPx
                finalH = containerWidthPx / videoAspect
            }
            finalW.toDp() to finalH.toDp()
        }

        val renderWidthPx = with(density) { renderWidthDp.toPx() }
        val renderHeightPx = with(density) { renderHeightDp.toPx() }

        // 1. Precise Aspect-Ratio Fitted Video Box & 1:1 Accurate Touch Area
        Box(
            modifier = Modifier
                .size(renderWidthDp, renderHeightDp)
                .align(Alignment.Center)
                .pointerInteropFilter { motionEvent ->
                    if (renderWidthPx > 0f && renderHeightPx > 0f) {
                        // Coordinates map directly 1:1 to the native video resolution
                        val tvX = ((motionEvent.x / renderWidthPx) * videoW).toInt().coerceIn(0, videoW)
                        val tvY = ((motionEvent.y / renderHeightPx) * videoH).toInt().coerceIn(0, videoH)

                        when (motionEvent.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
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
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                scrcpyClient.start(
                                    surface = holder.surface,
                                    dadb = dadb,
                                    cacheDir = ctx.cacheDir,
                                    assets = ctx.assets,
                                    scope = scope
                                )
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                scrcpyClient.stop()
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Loading State Overlay
        AnimatedVisibility(
            visible = mirrorState is ScrcpyState.Connecting,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val msg = (mirrorState as? ScrcpyState.Connecting)?.message ?: "Connecting mirror..."
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(36.dp))
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Error State Overlay
        AnimatedVisibility(
            visible = mirrorState is ScrcpyState.Error,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val err = (mirrorState as? ScrcpyState.Error)?.message ?: "Mirroring failed"
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(44.dp))
                    Text(
                        text = "Mirror Connection Error",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = err,
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }

        // 4. Floating Translucent Control Pill (Top or Bottom)
        Surface(
            shape = CircleShape,
            color = Color(0xCC181A20),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Back Key
                IconButton(
                    onClick = { scrcpyClient.sendKeyCode(TvKeys.BACK) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Home Key
                IconButton(
                    onClick = { scrcpyClient.sendKeyCode(TvKeys.HOME) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Menu Key
                IconButton(
                    onClick = { scrcpyClient.sendKeyCode(TvKeys.MENU) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                VerticalDivider(
                    color = Color.DarkGray.copy(alpha = 0.6f),
                    modifier = Modifier
                        .height(22.dp)
                        .padding(horizontal = 4.dp)
                )

                // Rotate Screen Toggle (Landscape / Portrait)
                IconButton(
                    onClick = {
                        isLandscape = !isLandscape
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (isLandscape) Icons.Default.ScreenLockPortrait else Icons.Default.ScreenRotation,
                        contentDescription = "Rotate",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Close / Exit Fullscreen
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Fullscreen", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
