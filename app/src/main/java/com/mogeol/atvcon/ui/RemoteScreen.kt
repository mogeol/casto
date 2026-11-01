package com.mogeol.atvcon.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.mogeol.atvcon.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mogeol.atvcon.adb.TvRemoteManager
import com.mogeol.atvcon.data.ConnectionState
import com.mogeol.atvcon.data.HostManager
import com.mogeol.atvcon.data.PowerAction
import com.mogeol.atvcon.data.RemoteTab
import com.mogeol.atvcon.data.TvApp
import com.mogeol.atvcon.data.TvKeys
import com.mogeol.atvcon.discovery.TvDiscoveryHelper
import com.mogeol.atvcon.scrcpy.ScrcpyMirrorView
import com.mogeol.atvcon.ui.components.*
import com.mogeol.atvcon.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen(
    remoteManager: TvRemoteManager,
    pendingUrl: String? = null,
    onClearPendingUrl: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val connectionState by remoteManager.connectionState.collectAsStateWithLifecycle()

    val hostManager = remember { HostManager(context) }
    var targetIp by remember { mutableStateOf(hostManager.lastConnectedHost) }
    var historyHosts by remember { mutableStateOf(hostManager.getHistoryHosts()) }
    var hostDropdownExpanded by remember { mutableStateOf(false) }
    var showCustomIpDialog by remember { mutableStateOf(false) }
    var customIpInput by remember { mutableStateOf("") }

    var selectedTab by remember { mutableStateOf(RemoteTab.REMOTE) }
    var isScreenFullscreen by remember { mutableStateOf(false) }
    var isRemoteLandscape by remember { mutableStateOf(true) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showSendLinkDialog by remember { mutableStateOf(false) }
    var linkToLaunch by remember { mutableStateOf("") }
    var isTakingScreenshot by remember { mutableStateOf(false) }
    var screenshotFileToShow by remember { mutableStateOf<File?>(null) }
    var showPowerMenu by remember { mutableStateOf(false) }
    var pendingPowerAction by remember { mutableStateOf<PowerAction?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    // Synchronize activity orientation and immersive system bars with mirror fullscreen
    LaunchedEffect(isScreenFullscreen, isRemoteLandscape) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isScreenFullscreen) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isRemoteLandscape) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Reset fullscreen if user navigates away from Screen tab
    LaunchedEffect(selectedTab) {
        if (selectedTab != RemoteTab.SCREEN && isScreenFullscreen) {
            isScreenFullscreen = false
        }
    }

    // Respond to shared URL from external intents
    LaunchedEffect(pendingUrl) {
        if (!pendingUrl.isNullOrBlank()) {
            linkToLaunch = pendingUrl
            showSendLinkDialog = true
            onClearPendingUrl()
        }
    }

    // Keep remoteManager informed of the current target host
    LaunchedEffect(targetIp) {
        remoteManager.setTargetHost(targetIp)
    }

    // Save host to history on successful connection
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            val ip = (connectionState as ConnectionState.Connected).ip
            hostManager.lastConnectedHost = ip
            historyHosts = hostManager.getHistoryHosts()
        }
    }

    // Lifecycle observer: When switching back to the app from foreground (ON_RESUME),
    // verify connection health. If connection was lost/dropped in background, auto-reconnect!
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    remoteManager.checkAndResumeConnection(targetIp)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // App list state
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var isAppsLoading by remember { mutableStateOf(false) }

    // Auto refresh apps when switching to Apps tab or reconnecting
    LaunchedEffect(selectedTab, connectionState) {
        if (selectedTab == RemoteTab.APPS && connectionState is ConnectionState.Connected) {
            if (apps.isEmpty()) {
                isAppsLoading = true
                remoteManager.getInstalledApps()
                    .onSuccess { apps = it }
                    .onFailure {
                        Toast.makeText(context, "Failed to load apps: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                isAppsLoading = false
            }
        }
    }

    // Discovery state
    var isScanning by remember { mutableStateOf(false) }
    var discoveredIps by remember { mutableStateOf<List<String>>(emptyList()) }
    var showScanDialog by remember { mutableStateOf(false) }

    // Helper to send keys safely
    val sendKey: (Int) -> Unit = { keyCode ->
        coroutineScope.launch {
            remoteManager.sendKey(keyCode).onFailure {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isScreenFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                // Header Row: Host Dropdown Pill (Left) & Global Action Icons (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Compact Host Dropdown Pill + Quick Connect Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = DarkSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (connectionState is ConnectionState.Connected) AccentGreen.copy(alpha = 0.6f) else Color.DarkGray
                                ),
                                modifier = Modifier.clickable { hostDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (connectionState) {
                                                    is ConnectionState.Connected -> AccentGreen
                                                    is ConnectionState.Connecting -> Color.Yellow
                                                    else -> AccentRed
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = targetIp.ifBlank { stringResource(R.string.select_host) },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = stringResource(R.string.select_host),
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = hostDropdownExpanded,
                                onDismissRequest = { hostDropdownExpanded = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                Text(
                                    text = stringResource(R.string.saved_devices),
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )

                                historyHosts.forEach { ip ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (ip == targetIp && connectionState is ConnectionState.Connected) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Text(ip, color = Color.White, fontSize = 13.sp)
                                            }
                                        },
                                        onClick = {
                                            hostDropdownExpanded = false
                                            targetIp = ip
                                            coroutineScope.launch { remoteManager.connect(ip) }
                                        }
                                    )
                                }

                                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.custom_ip), color = AccentCyan, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        hostDropdownExpanded = false
                                        customIpInput = targetIp
                                        showCustomIpDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.scan_lan_devices), color = AccentCyan, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        hostDropdownExpanded = false
                                        coroutineScope.launch {
                                            isScanning = true
                                            val prefix = TvDiscoveryHelper.getLocalSubnetPrefix() ?: "192.168.5"
                                            discoveredIps = TvDiscoveryHelper.scanLocalSubnet(prefix)
                                            isScanning = false
                                            showScanDialog = true
                                        }
                                    }
                                )

                                 HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.support_title), color = Color.White, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        hostDropdownExpanded = false
                                        showSupportDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.about_title), color = Color.White, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        hostDropdownExpanded = false
                                        showAboutDialog = true
                                    }
                                )

                                if (connectionState is ConnectionState.Connected) {
                                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(stringResource(R.string.disconnect), color = AccentRed, fontSize = 13.sp)
                                            }
                                        },
                                        onClick = {
                                            hostDropdownExpanded = false
                                            remoteManager.disconnect()
                                        }
                                    )
                                }
                            }
                        }

                        // Compact Connect Button when disconnected
                        if (connectionState !is ConnectionState.Connected) {
                            FilledTonalButton(
                                onClick = { coroutineScope.launch { remoteManager.connect(targetIp) } },
                                enabled = connectionState !is ConnectionState.Connecting,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF262A34),
                                    contentColor = AccentCyan
                                )
                            ) {
                                Text(
                                    if (connectionState is ConnectionState.Connecting) stringResource(R.string.connecting) else stringResource(R.string.connect),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Right: Action Cluster (Support, Screen, Screenshot, Send Link, Settings, Power)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        // Support & Donation Dialog Shortcut
                        IconButton(
                            onClick = { showSupportDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = stringResource(R.string.support_title),
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Fast Jump to Screen Mirror Tab
                        IconButton(
                            onClick = {
                                selectedTab = RemoteTab.SCREEN
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Tv,
                                contentDescription = stringResource(R.string.action_screen_mirror),
                                tint = if (selectedTab == RemoteTab.SCREEN) AccentCyan else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // TV Screenshot
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isTakingScreenshot = true
                                    val cacheFile = File(context.cacheDir, "screenshot_tv_${System.currentTimeMillis()}.png")
                                    remoteManager.takeScreenshot(cacheFile).onSuccess { file ->
                                        screenshotFileToShow = file
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Screenshot error: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    isTakingScreenshot = false
                                }
                            },
                            enabled = !isTakingScreenshot && connectionState is ConnectionState.Connected,
                            modifier = Modifier.size(34.dp)
                        ) {
                            if (isTakingScreenshot) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.action_screenshot), tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                        }

                        // Send Link to TV
                        IconButton(
                            onClick = {
                                linkToLaunch = ""
                                showSendLinkDialog = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = stringResource(R.string.action_send_link), tint = Color.White, modifier = Modifier.size(19.dp))
                        }

                        // Settings Button (Moved next to Power!)
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    remoteManager.openSettings().onSuccess {
                                        Toast.makeText(context, "Opening TV Settings...", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_tv_settings), tint = Color.White, modifier = Modifier.size(19.dp))
                        }

                        // Power Button with Pop-out Menu
                        Box {
                            IconButton(
                                onClick = { showPowerMenu = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = stringResource(R.string.action_power_menu),
                                    tint = AccentRed,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showPowerMenu,
                                onDismissRequest = { showPowerMenu = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                Text(
                                    text = stringResource(R.string.power_controls),
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )

                                // 1. Sleep / Wake (Standard Power Key)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Brightness4, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(stringResource(PowerAction.SLEEP.titleRes), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(stringResource(PowerAction.SLEEP.subtitleRes), color = TextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showPowerMenu = false
                                        coroutineScope.launch {
                                            remoteManager.executePowerAction(PowerAction.SLEEP)
                                        }
                                    }
                                )

                                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))

                                // 2. Restart
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(stringResource(PowerAction.RESTART.titleRes), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(stringResource(PowerAction.RESTART.subtitleRes), color = TextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showPowerMenu = false
                                        pendingPowerAction = PowerAction.RESTART
                                    }
                                )

                                // 3. Restart to Recovery
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(stringResource(PowerAction.RECOVERY.titleRes), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(stringResource(PowerAction.RECOVERY.subtitleRes), color = TextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showPowerMenu = false
                                        pendingPowerAction = PowerAction.RECOVERY
                                    }
                                )

                                // 4. Restart to Fastboot
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFBA68C8), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(stringResource(PowerAction.FASTBOOT.titleRes), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(stringResource(PowerAction.FASTBOOT.subtitleRes), color = TextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showPowerMenu = false
                                        pendingPowerAction = PowerAction.FASTBOOT
                                    }
                                )

                                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))

                                // 5. Power Off (Shutdown)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(stringResource(PowerAction.POWER_OFF.titleRes), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(stringResource(PowerAction.POWER_OFF.subtitleRes), color = TextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showPowerMenu = false
                                        pendingPowerAction = PowerAction.POWER_OFF
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sticky Global Navigation Toolbar: Back, Home, Search, Menu (Sticky across ALL tabs!)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E2129),
                    border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back
                        IconButton(
                            onClick = { sendKey(TvKeys.BACK) },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.nav_back), tint = Color.White, modifier = Modifier.size(22.dp))
                        }

                        // Home
                        IconButton(
                            onClick = { sendKey(TvKeys.HOME) },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = stringResource(R.string.nav_home), tint = Color.White, modifier = Modifier.size(22.dp))
                        }

                        // App Switcher / Task Switcher (scrcpy Ctrl + S / KEYCODE_APP_SWITCH)
                        IconButton(
                            onClick = { sendKey(TvKeys.APP_SWITCH) },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(Icons.Default.FilterNone, contentDescription = stringResource(R.string.nav_app_switch), tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // Search / Submit
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    remoteManager.submitSearch().onSuccess {
                                        Toast.makeText(context, "Search submitted", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search), tint = Color.White, modifier = Modifier.size(22.dp))
                        }

                        // Menu (Click for short press, Long-press for context menu)
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        coroutineScope.launch {
                                            remoteManager.openMenu(false).onFailure {
                                                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        coroutineScope.launch {
                                            remoteManager.openMenu(true).onSuccess {
                                                Toast.makeText(context, "Long-press Menu sent", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_menu), tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    },
        bottomBar = {
            if (!isScreenFullscreen) {
                NavigationBar(
                    containerColor = DarkSurface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    RemoteTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                when (tab) {
                                    RemoteTab.REMOTE -> Icon(Icons.Default.SettingsRemote, contentDescription = null)
                                    RemoteTab.SCREEN -> Icon(Icons.Default.Tv, contentDescription = null)
                                    RemoteTab.APPS -> Icon(Icons.Default.Apps, contentDescription = null)
                                    RemoteTab.FILES -> Icon(Icons.Default.Folder, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.titleRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentCyan,
                                selectedTextColor = AccentCyan,
                                indicatorColor = DarkSurfaceVariant,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isScreenFullscreen) PaddingValues(0.dp) else paddingValues)
        ) {
            when (selectedTab) {
                RemoteTab.REMOTE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 1. Integrated D-Pad Housing (Circular D-Pad + Lower-Flank Back & Play)
                        DPad(
                            size = 312.dp,
                            onKeyClick = sendKey,
                            onKeyLongClick = { keyCode ->
                                coroutineScope.launch {
                                    remoteManager.sendKey(keyCode, longPress = true).onFailure {
                                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Segmented 3-Button Pill Row: [ Backspace | Keyboard | Mute ] (Width 312dp, Height 52dp)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF1E2129),
                            border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .width(312.dp)
                                .height(52.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Backspace / DEL (Left cell)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = rememberRipple(bounded = true, color = AccentCyan)
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            sendKey(TvKeys.DEL)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace / Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(1.dp)
                                        .padding(vertical = 10.dp)
                                        .background(Color.DarkGray.copy(alpha = 0.45f))
                                )

                                // Keyboard (Center cell)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = rememberRipple(bounded = true, color = AccentCyan)
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showTextInputDialog = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Keyboard,
                                        contentDescription = "Text Input",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(1.dp)
                                        .padding(vertical = 10.dp)
                                        .background(Color.DarkGray.copy(alpha = 0.45f))
                                )

                                // Mute (Right cell)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = rememberRipple(bounded = true, color = AccentCyan)
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            sendKey(TvKeys.VOLUME_MUTE)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeMute,
                                        contentDescription = "Mute",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Stretched Bottom Set: [ CH Rocker ] [ ScrollPad (Page Up/Down) ] [ VOL Rocker ] (Height 224dp)
                        Row(
                            modifier = Modifier.width(312.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Stretched Vertical CH Rocker
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = DarkSurfaceVariant,
                                border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(224.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(vertical = 10.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch { remoteManager.channelNext(up = true) }
                                        },
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Next Track / CH +",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Text(
                                        text = "CH",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch { remoteManager.channelNext(up = false) }
                                        },
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Previous Track / CH -",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

                            // Center: Scroll Pad for Page Scrolling (Up & Down / Drag & Tap)
                            ScrollPad(
                                onPageScroll = { down ->
                                    coroutineScope.launch { remoteManager.pageScroll(down) }
                                },
                                width = 114.dp,
                                height = 224.dp
                            )

                            // Stretched Vertical VOL Rocker
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = DarkSurfaceVariant,
                                border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(224.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(vertical = 10.dp)
                                ) {
                                    IconButton(
                                        onClick = { sendKey(TvKeys.VOLUME_UP) },
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Vol +",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text(
                                        text = "VOL",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    IconButton(
                                        onClick = { sendKey(TvKeys.VOLUME_DOWN) },
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = "Vol -",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                RemoteTab.SCREEN -> {
                    ScreenMirrorTab(
                        dadb = remoteManager.currentDadb,
                        targetIp = targetIp,
                        isConnected = connectionState is ConnectionState.Connected,
                        isFullscreen = isScreenFullscreen,
                        onToggleFullscreen = { isScreenFullscreen = it },
                        onRemoteOrientationChanged = { isRemoteLandscape = it },
                        onSendKey = sendKey,
                        onChannelNext = { up -> coroutineScope.launch { remoteManager.channelNext(up) } },
                        onOpenKeyboard = { showTextInputDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                RemoteTab.APPS -> {
                    AppLauncherSheet(
                        apps = apps,
                        isLoading = isAppsLoading,
                        onRefresh = {
                            coroutineScope.launch {
                                isAppsLoading = true
                                remoteManager.getInstalledApps()
                                    .onSuccess { apps = it }
                                    .onFailure {
                                        Toast.makeText(context, "Failed to load apps: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                isAppsLoading = false
                            }
                        },
                        onLaunch = { pkg ->
                            coroutineScope.launch {
                                remoteManager.launchApp(pkg)
                            }
                        },
                        onKill = { pkg ->
                            coroutineScope.launch {
                                remoteManager.killApp(pkg)
                                Toast.makeText(context, "Force stopped $pkg", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenLinkClick = {
                            linkToLaunch = ""
                            showSendLinkDialog = true
                        }
                    )
                }

                RemoteTab.FILES -> {
                    FileBrowserSheet(remoteManager = remoteManager)
                }
            }
        }
    }

    // Text Input Modal
    if (showTextInputDialog) {
        TextInputDialog(
            onDismiss = { showTextInputDialog = false },
            onSend = { text, submitSearch ->
                coroutineScope.launch {
                    remoteManager.sendText(text).onSuccess {
                        if (submitSearch) {
                            remoteManager.submitSearch()
                            Toast.makeText(context, "Text sent & Search executed", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Pasted text to TV", Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure {
                        Toast.makeText(context, "Failed to send text: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Remote Intent Link Launcher Modal
    if (showSendLinkDialog) {
        SendLinkDialog(
            initialUrl = linkToLaunch,
            onDismiss = { showSendLinkDialog = false },
            onLaunchLink = { url ->
                coroutineScope.launch {
                    remoteManager.openLink(url).onSuccess {
                        Toast.makeText(context, "Launched on TV via default intent", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "Launch failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Custom IP Entry Dialog
    if (showCustomIpDialog) {
        AlertDialog(
            onDismissRequest = { showCustomIpDialog = false },
            title = { Text("Connect to TV IP", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = customIpInput,
                    onValueChange = { customIpInput = it },
                    singleLine = true,
                    placeholder = { Text("e.g. 192.168.1.10", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ip = customIpInput.trim()
                        if (ip.isNotEmpty()) {
                            targetIp = ip
                            showCustomIpDialog = false
                            coroutineScope.launch { remoteManager.connect(ip) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomIpDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Screenshot Preview Modal
    val screenFile = screenshotFileToShow
    if (screenFile != null) {
        ScreenshotPreviewDialog(
            screenshotFile = screenFile,
            onDismiss = { screenshotFileToShow = null }
        )
    }

    // LAN Discovery Results Dialog
    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Discovered ADB Devices") },
            text = {
                if (discoveredIps.isEmpty()) {
                    Text("No ADB devices responding on port 5555. Ensure Network Debugging is enabled on your TV.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discoveredIps.forEach { ip ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        targetIp = ip
                                        showScanDialog = false
                                        coroutineScope.launch { remoteManager.connect(ip) }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = AccentCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = ip, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScanDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Power Action Confirmation Dialog
    pendingPowerAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingPowerAction = null },
            containerColor = DarkSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (action == PowerAction.POWER_OFF) Icons.Default.PowerSettingsNew else Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = if (action == PowerAction.POWER_OFF) AccentRed else AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(action.titleRes)}?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Text(
                    text = "${stringResource(action.subtitleRes)}.\n\nTarget: ${targetIp.ifBlank { "Connected Device" }}\n${stringResource(R.string.power_dialog_prompt)}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val act = action
                        pendingPowerAction = null
                        coroutineScope.launch {
                            remoteManager.executePowerAction(act)
                            Toast.makeText(context, "Sent ${act.title} to $targetIp", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == PowerAction.POWER_OFF) AccentRed else AccentGreen
                    )
                ) {
                    Text(
                        text = if (action == PowerAction.POWER_OFF) stringResource(R.string.power_dialog_confirm_off) else stringResource(R.string.power_dialog_confirm_restart),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPowerAction = null }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismissRequest = { showAboutDialog = false },
            onOpenSupport = {
                showAboutDialog = false
                showSupportDialog = true
            }
        )
    }

    if (showSupportDialog) {
        SupportDialog(onDismissRequest = { showSupportDialog = false })
    }
}
