package com.mogeol.atvcon.adb

import com.mogeol.atvcon.data.AppResolver
import com.mogeol.atvcon.data.ConnectionState
import com.mogeol.atvcon.data.PowerAction
import com.mogeol.atvcon.data.TvApp
import com.mogeol.atvcon.data.TvFileItem
import com.mogeol.atvcon.data.TvKeys
import com.mogeol.atvcon.data.TvScreenSize
import dadb.AdbKeyPair
import dadb.AdbShellResponse
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class TvRemoteManager(private val cacheDir: File) {

    private var dadb: Dadb? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var cachedScreenSize: TvScreenSize? = null
    private val isScrollActive = AtomicBoolean(false)

    var lastKnownIp: String? = null
        private set

    val isConnected: Boolean
        get() = _connectionState.value is ConnectionState.Connected

    val currentDadb: Dadb?
        get() = dadb

    fun setTargetHost(ip: String) {
        lastKnownIp = ip
    }

    /**
     * Test whether the current ADB socket is alive.
     * If dead or disconnected, closes the socket cleanly and sets state to Disconnected.
     */
    suspend fun isConnectionAlive(): Boolean = withContext(Dispatchers.IO) {
        val client = dadb ?: return@withContext false
        try {
            val res = client.shell("echo 1")
            val ok = res.output.trim() == "1"
            if (!ok) {
                handleSocketFailure(IOException("Ping response invalid"))
            }
            ok
        } catch (e: Exception) {
            handleSocketFailure(e)
            false
        }
    }

    /**
     * Called when the app returns to the foreground.
     * Tests socket health. If the TCP connection was closed or dropped while the app
     * was in the background, automatically re-establishes the connection.
     */
    suspend fun checkAndResumeConnection(preferredIp: String? = null) = withContext(Dispatchers.IO) {
        val target = preferredIp ?: lastKnownIp ?: (connectionState.value as? ConnectionState.Connected)?.ip
        if (target.isNullOrBlank()) return@withContext
        lastKnownIp = target

        val client = dadb
        if (client != null && _connectionState.value is ConnectionState.Connected) {
            val alive = isConnectionAlive()
            if (!alive) {
                // Connection was dropped during background pause; re-establish it now
                connect(target)
            }
        } else if (_connectionState.value !is ConnectionState.Connecting) {
            // Was disconnected or in error; auto-reconnect on foreground resume
            connect(target)
        }
    }

    fun handleSocketFailure(err: Throwable) {
        try {
            dadb?.close()
        } catch (_: Exception) {}
        dadb = null
        cachedScreenSize = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Executes a shell command with auto-recovery.
     * If the socket was dropped while the app was in the background, reconnects automatically and retries once.
     */
    suspend fun executeShellWithRetry(command: String): AdbShellResponse = withContext(Dispatchers.IO) {
        val client = dadb
        if (client == null) {
            val ip = lastKnownIp ?: throw IllegalStateException("Device not connected")
            connect(ip).getOrThrow()
            return@withContext dadb?.shell(command) ?: throw IllegalStateException("Device not connected")
        }

        try {
            client.shell(command)
        } catch (e: Exception) {
            handleSocketFailure(e)
            val ip = lastKnownIp
            if (ip != null) {
                // Socket was broken, reconnect and retry command once!
                connect(ip).getOrThrow()
                dadb?.shell(command) ?: throw e
            } else {
                throw e
            }
        }
    }

    /**
     * Connect to TV over TCP/IP socket
     */
    suspend fun connect(ip: String, port: Int = 5555): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            lastKnownIp = ip
            disconnect()
            _connectionState.value = ConnectionState.Connecting

            val privKey = File(cacheDir, "adbkey")
            val pubKey = File(cacheDir, "adbkey.pub")

            val keyPair = if (privKey.exists() && pubKey.exists()) {
                AdbKeyPair.read(privKey, pubKey)
            } else {
                AdbKeyPair.generate(privKey, pubKey)
                AdbKeyPair.read(privKey, pubKey)
            }

            // Create client & perform ADB handshake
            val client = Dadb.create(ip, port, keyPair)
            dadb = client
            _connectionState.value = ConnectionState.Connected(ip, port)

            // Cache screen size in background
            try {
                cachedScreenSize = queryScreenSize(client)
            } catch (_: Exception) {
                cachedScreenSize = TvScreenSize(1920, 1080)
            }

            // Enable visual touch/cursor feedback on TV screen
            try {
                client.shell("settings put system show_touches 1")
            } catch (_: Exception) {
                // Ignore if permission not granted
            }

            Unit
        }.onFailure { err ->
            _connectionState.value = ConnectionState.Error(err.message ?: "Connection failed")
        }
    }

    /**
     * Send standard Android key event
     */
    suspend fun sendKey(keyCode: Int, longPress: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cmd = if (longPress) "input keyevent --longpress $keyCode" else "input keyevent $keyCode"
            executeShellWithRetry(cmd)
            Unit
        }
    }

    /**
     * Open TV system settings.
     * Uses direct intent launch (supported on all Android TV boxes and Google TVs),
     * with fallback to KEYCODE_SETTINGS (176).
     */
    suspend fun openSettings(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val res = executeShellWithRetry("am start -a android.settings.SETTINGS")
            if (res.exitCode != 0) {
                executeShellWithRetry("input keyevent ${TvKeys.SETTINGS}")
            }
            Unit
        }
    }

    /**
     * Trigger Search / Submit action when an IME keyboard or search field is focused.
     * Dispatches KEYCODE_ENTER (66) and KEYCODE_SEARCH (84) to confirm and execute.
     */
    suspend fun submitSearch(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            executeShellWithRetry("input keyevent ${TvKeys.ENTER} ${TvKeys.SEARCH}")
            Unit
        }
    }

    /**
     * Open Menu (short click or long-press context menu).
     */
    suspend fun openMenu(longPress: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cmd = if (longPress) "input keyevent --longpress ${TvKeys.MENU}" else "input keyevent ${TvKeys.MENU}"
            executeShellWithRetry(cmd)
            Unit
        }
    }

    /**
     * Send text input (supports Unicode/Chinese via clipboard fallback)
     */
    suspend fun sendText(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val isAsciiWithoutSpaces = text.all { it.code in 33..126 } && !text.contains("\"") && !text.contains("$")

            if (isAsciiWithoutSpaces) {
                executeShellWithRetry("input text \"$text\"")
            } else {
                val safeText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
                executeShellWithRetry("cmd clipboard set text \"$safeText\"")
                executeShellWithRetry("input keyevent ${TvKeys.KEYCODE_PASTE}")
            }
            Unit
        }
    }

    /**
     * Touchpad / Mouse Tap
     */
    suspend fun tap(x: Int, y: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            executeShellWithRetry("input tap $x $y")
            Unit
        }
    }

    /**
     * Touchpad / Mouse Swipe (Drag)
     */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 200): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            executeShellWithRetry("input swipe $x1 $y1 $x2 $y2 $durationMs")
            Unit
        }
    }

    /**
     * Delicate reading scroll for web pages, articles, documents, and feeds.
     * Uses a short, gentle drag (10% of screen height ~110px @ 160ms) without triggering
     * high-velocity flings or conflicting D-pad focus resets.
     * Concurrency-debounced via AtomicBoolean to prevent input queue jamming.
     */
    suspend fun pageScroll(down: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isScrollActive.compareAndSet(false, true)) {
            // Already processing a scroll stroke, skip to avoid queue jamming
            return@withContext Result.success(Unit)
        }
        try {
            runCatching {
                val (w, h) = cachedScreenSize?.let { it.width to it.height } ?: (1920 to 1080)
                val cx = w / 2
                val cy = h / 2
                // Reading scroll step (~18% of screen height ~194px on 1080p, slightly bigger for comfortable reading)
                val delta = (h * 0.18).toInt().coerceIn(160, 260)
                // When down == true (scrolling down / page down), drag from top to bottom (cy - delta/2 -> cy + delta/2) to move page down
                // When down == false (scrolling up / page up), drag from bottom to top (cy + delta/2 -> cy - delta/2) to move page up
                val y1 = if (down) cy - (delta / 2) else cy + (delta / 2)
                val y2 = if (down) cy + (delta / 2) else cy - (delta / 2)

                // Pure touch scroll gesture - smooth and responsive (180ms)
                executeShellWithRetry("input swipe $cx $y1 $cx $y2 180")
                Unit
            }
        } finally {
            isScrollActive.set(false)
        }
    }

    /**
     * Channel / Track Next or Previous.
     * Dispatches both MEDIA_NEXT/PREVIOUS (for SmartTube, YouTube, VLC, Spotify, Netflix)
     * and CHANNEL_UP/DOWN (for broadcast/IPTV tuners).
     *
     * @param up True for Next / Channel Up, False for Previous / Channel Down.
     */
    suspend fun channelNext(up: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaKey = if (up) TvKeys.MEDIA_NEXT else TvKeys.MEDIA_PREVIOUS
            val chKey = if (up) TvKeys.CHANNEL_UP else TvKeys.CHANNEL_DOWN
            executeShellWithRetry("input keyevent $mediaKey $chKey")
            Unit
        }
    }

    /**
     * Fast Seek for Video & Audio tracks.
     * Supports all TV players (SmartTube, YouTube TV, VLC, MX Player, Kodi, Netflix):
     * 1. Injects D-Pad Left/Right seek bursts (e.g. keyevent 22 22 22).
     * 2. Injects horizontal scrub swipe for touch-seeking media engines (VLC, MX Player).
     *
     * @param forward True to seek forward, false to rewind.
     * @param multiplier Seek intensity: 1 (short ~10s), 2 (medium ~30s), 3 (fast ~1m+).
     */
    suspend fun fastSeek(forward: Boolean, multiplier: Int = 1): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (w, h) = cachedScreenSize?.let { it.width to it.height } ?: (1920 to 1080)
            val cy = h / 2

            // 1. D-Pad seek burst (SmartTube / YouTube TV / Leanback seek)
            val key = if (forward) TvKeys.RIGHT else TvKeys.LEFT
            val count = when (multiplier) {
                1 -> 1
                2 -> 3
                3 -> 6
                else -> 8
            }
            val keysStr = List(count) { key }.joinToString(" ")
            executeShellWithRetry("input keyevent $keysStr")

            // 2. Horizontal swipe across video surface (VLC / MX Player / Web players)
            val span = (w * 0.28 * multiplier.coerceAtMost(3) / 2.0).toInt().coerceAtLeast(200)
            val x1 = if (forward) (w / 2) - span else (w / 2) + span
            val x2 = if (forward) (w / 2) + span else (w / 2) - span
            executeShellWithRetry("input swipe $x1 $cy $x2 $cy 200")
            Unit
        }
    }

    /**
     * Get TV screen size (e.g. 1920x1080)
     */
    fun getScreenSize(): TvScreenSize {
        return cachedScreenSize ?: TvScreenSize(1920, 1080)
    }

    private fun queryScreenSize(client: Dadb): TvScreenSize {
        val output = client.shell("wm size").output
        val regex = Regex("(\\d+)x(\\d+)")
        val match = regex.find(output) ?: return TvScreenSize(1920, 1080)
        val (w, h) = match.destructured
        return TvScreenSize(w.toInt(), h.toInt())
    }

    /**
     * Enable or disable visual touch/cursor indicator on the TV display.
     */
    suspend fun setShowTouches(enable: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            client.shell("settings put system show_touches ${if (enable) 1 else 0}")
            Unit
        }
    }

    /**
     * Fetch installed 3rd-party TV apps with resolved names and icons
     */
    suspend fun getInstalledApps(): Result<List<TvApp>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            val output = client.shell("pm list packages -3").output
            output.lines()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .map { pkg -> AppResolver.resolve(pkg) }
                .sortedBy { it.displayName.lowercase() }
        }
    }

    /**
     * Launch an app by package name
     */
    suspend fun launchApp(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            client.shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
            Unit
        }
    }

    /**
     * Remotely open a URL or deep link on the Android TV using the default intent (ACTION_VIEW).
     * This will trigger the default browser, YouTube app, video player, or any registered
     * intent handler on the TV.
     *
     * @param url The target URI or web URL (e.g. "https://...", "https://youtu.be/...", "market://...", etc.)
     * @return Result containing ADB shell command output or error
     */
    suspend fun openLink(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            val trimmed = url.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("URL / link cannot be empty")
            }

            // Automatically prepend https:// if no URI scheme is provided
            val fullUrl = if (!trimmed.contains("://") && !trimmed.startsWith("market:") && !trimmed.startsWith("intent:")) {
                "https://$trimmed"
            } else {
                trimmed
            }

            // Escape single quotes for adb shell execution
            val safeUrl = fullUrl.replace("'", "'\\''")
            // am start -a android.intent.action.VIEW -d <URI>
            val response = client.shell("am start -a android.intent.action.VIEW -d '$safeUrl'")
            if (response.exitCode != 0) {
                throw IOException("Intent launch failed (${response.exitCode}): ${response.output}")
            }
            response.output.trim().ifEmpty { "Default intent launched successfully" }
        }
    }

    /**
     * Force stop an app
     */
    suspend fun killApp(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            client.shell("am force-stop $packageName")
            Unit
        }
    }

    /**
     * Set TV volume (Stream 3 = STREAM_MUSIC)
     */
    suspend fun setVolume(level: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            client.shell("cmd media_session volume --stream 3 --set $level")
            Unit
        }
    }

    /**
     * Get TV volume (Stream 3)
     */
    suspend fun getVolume(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            val output = client.shell("cmd media_session volume --stream 3 --get").output
            val regex = Regex("volume is (\\d+)")
            val match = regex.find(output) ?: throw IllegalStateException("Unable to read volume")
            match.groupValues[1].toInt()
        }
    }

    /**
     * Toggle Mute
     */
    suspend fun toggleMute(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            client.shell("input keyevent ${TvKeys.VOLUME_MUTE}")
            Unit
        }
    }

    /**
     * Capture a screenshot of the TV screen and pull it to the local device.
     */
    suspend fun takeScreenshot(localOutputFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            val remoteTemp = "/data/local/tmp/screencap_${System.currentTimeMillis()}.png"
            val res = client.shell("screencap -p $remoteTemp")
            if (res.exitCode != 0) {
                throw IOException("screencap command failed: ${res.output}")
            }
            try {
                if (localOutputFile.exists()) localOutputFile.delete()
                client.pull(localOutputFile, remoteTemp)
                if (!localOutputFile.exists() || localOutputFile.length() == 0L) {
                    throw IOException("Failed to pull screenshot from TV")
                }
                localOutputFile
            } finally {
                client.shell("rm -f $remoteTemp")
            }
        }
    }

    /**
     * List files and directories at a remote TV path.
     */
    suspend fun listFiles(remotePath: String): Result<List<TvFileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            val cleanPath = if (remotePath.endsWith("/")) remotePath else "$remotePath/"
            val safePath = cleanPath.replace("'", "'\\''")
            val res = client.shell("ls -la '$safePath'")
            if (res.exitCode != 0) {
                throw IOException("Unable to list directory ($remotePath): ${res.output}")
            }

            val items = mutableListOf<TvFileItem>()
            val pattern = Regex("""^([d\-lbcsp][rwxst\-]{9})\s+(\d+)\s+(\S+)\s+(\S+)\s+(\d+)\s+(\d{4}-\d{2}-\d{2})\s+([0-9:]+)\s+(.+)$""")

            for (line in res.output.lines()) {
                val trimmed = line.trim()
                val match = pattern.matchEntire(trimmed) ?: continue
                val perms = match.groupValues[1]
                val size = match.groupValues[5].toLongOrNull() ?: 0L
                val date = match.groupValues[6]
                val time = match.groupValues[7]
                var rawName = match.groupValues[8]

                if (rawName == "." || rawName == "..") continue

                val isSymlink = perms.startsWith('l')
                if (isSymlink && rawName.contains(" -> ")) {
                    rawName = rawName.substringBefore(" -> ").trim()
                }

                val isDir = perms.startsWith('d')
                val fullItemPath = if (cleanPath == "/") "/$rawName" else "$cleanPath$rawName"
                val ext = if (!isDir && rawName.contains('.')) rawName.substringAfterLast('.').lowercase() else ""

                items.add(
                    TvFileItem(
                        name = rawName,
                        path = fullItemPath,
                        isDirectory = isDir,
                        size = size,
                        modifiedTime = "$date $time",
                        extension = ext
                    )
                )
            }

            // Directories first, then alphabetical
            items.sortWith(compareBy<TvFileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            items
        }
    }

    /**
     * Push a local file to the TV filesystem.
     */
    suspend fun pushFile(localFile: File, remoteDestPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            // POSIX mode 420 is 0644
            client.push(localFile, remoteDestPath, 420, System.currentTimeMillis())
            Unit
        }
    }

    /**
     * Pull a remote file from the TV to the local device.
     */
    suspend fun pullFile(remotePath: String, localDestFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            if (localDestFile.exists()) localDestFile.delete()
            client.pull(localDestFile, remotePath)
            if (!localDestFile.exists()) {
                throw IOException("File pull failed")
            }
            localDestFile
        }
    }

    /**
     * Delete a file or directory on the TV.
     */
    suspend fun deleteRemoteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            val safe = remotePath.replace("'", "'\\''")
            val res = client.shell("rm -rf '$safe'")
            if (res.exitCode != 0) {
                throw IOException("Failed to delete ($remotePath): ${res.output}")
            }
            Unit
        }
    }

    /**
     * Install an APK already uploaded/present on the TV.
     */
    suspend fun installRemoteApk(remoteApkPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            val safe = remoteApkPath.replace("'", "'\\''")
            val res = client.shell("pm install -r -d -t '$safe'")
            if (res.exitCode != 0 || !res.output.contains("Success", ignoreCase = true)) {
                throw IOException("Install failed: ${res.output}")
            }
            res.output.trim()
        }
    }

    /**
     * Open a remote file on the TV using default intent.
     */
    suspend fun openRemoteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            val safe = remotePath.replace("'", "'\\''")
            client.shell("am start -a android.intent.action.VIEW -d 'file://$safe'")
            Unit
        }
    }

    /**
     * Executes advanced power/reboot actions on the target device
     */
    suspend fun executePowerAction(action: PowerAction): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = dadb ?: throw IllegalStateException("Device not connected")
            try {
                client.shell(action.command)
            } catch (_: Exception) {
                // Sockets frequently break or close abruptly when a device reboots / shuts down; this is normal
            }
            if (action.isDestructive) {
                try {
                    client.close()
                } catch (_: Exception) {}
                dadb = null
                cachedScreenSize = null
                _connectionState.value = ConnectionState.Disconnected
            }
            Unit
        }
    }

    /**
     * Disconnect socket
     */
    fun disconnect() {
        try {
            dadb?.close()
        } catch (_: IOException) {}
        dadb = null
        cachedScreenSize = null
        _connectionState.value = ConnectionState.Disconnected
    }
}

