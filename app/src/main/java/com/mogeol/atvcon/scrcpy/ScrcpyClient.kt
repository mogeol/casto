package com.mogeol.atvcon.scrcpy

import android.content.res.AssetManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import dadb.AdbShellStream
import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.BufferedSink
import okio.BufferedSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed class ScrcpyState {
    data object Idle : ScrcpyState()
    data class Connecting(val message: String) : ScrcpyState()
    data class Connected(val width: Int, val height: Int, val deviceName: String) : ScrcpyState()
    data class Error(val message: String) : ScrcpyState()
}

class ScrcpyClient {

    companion object {
        private const val TAG = "ScrcpyClient"
        private const val REMOTE_JAR_PATH = "/data/local/tmp/scrcpy-server.jar"
        private const val ASSET_JAR_NAME = "scrcpy-server.jar"
        private const val SCRCPY_VERSION = "2.7"
    }

    private val _state = MutableStateFlow<ScrcpyState>(ScrcpyState.Idle)
    val state: StateFlow<ScrcpyState> = _state.asStateFlow()

    private var workerJob: Job? = null
    private var controlJob: Job? = null
    private var shellStream: AdbShellStream? = null
    private var videoStream: AdbStream? = null
    private var controlStream: AdbStream? = null
    private var controlSink: BufferedSink? = null
    private var mediaCodec: MediaCodec? = null

    private val controlChannel = Channel<suspend (BufferedSink) -> Unit>(Channel.UNLIMITED)

    @Volatile
    private var isRunning = false

    @Volatile
    private var currentSurface: Surface? = null

    @Volatile
    private var isSurfaceValid = false

    var videoWidth: Int = 1920
        private set
    var videoHeight: Int = 1080
        private set

    /**
     * Start the scrcpy server on the remote TV, connect video and control streams,
     * and decode live video onto the provided Surface.
     */
    fun start(
        surface: Surface,
        dadb: Dadb,
        cacheDir: File,
        assets: AssetManager,
        maxSize: Int = 1280,
        bitRate: Int = 4_000_000,
        maxFps: Int = 60,
        scope: CoroutineScope
    ) {
        if (isRunning) {
            stop()
        }
        isRunning = true
        currentSurface = surface
        isSurfaceValid = surface.isValid

        workerJob = scope.launch(Dispatchers.IO) {
            try {
                _state.value = ScrcpyState.Connecting("Checking server on TV...")

                // 1. Prepare local asset jar
                val localJar = File(cacheDir, ASSET_JAR_NAME)
                if (!localJar.exists() || localJar.length() == 0L) {
                    assets.open(ASSET_JAR_NAME).use { input ->
                        FileOutputStream(localJar).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Check if already present on TV
                val checkRes = runCatching { dadb.shell("test -f $REMOTE_JAR_PATH && echo OK") }.getOrNull()?.output ?: ""
                if (!checkRes.contains("OK")) {
                    _state.value = ScrcpyState.Connecting("Pushing server to TV...")
                    dadb.push(localJar, REMOTE_JAR_PATH, 420, System.currentTimeMillis())
                }

                _state.value = ScrcpyState.Connecting("Starting scrcpy server...")

                // 2. Launch scrcpy-server in background via openShell
                val fpsArg = if (maxFps > 0) " max_fps=$maxFps" else ""
                val serverCmd = "CLASSPATH=$REMOTE_JAR_PATH app_process / com.genymobile.scrcpy.Server $SCRCPY_VERSION " +
                        "tunnel_forward=true audio=false control=true max_size=$maxSize video_bit_rate=$bitRate$fpsArg"
                shellStream = dadb.openShell(serverCmd)

                // 3. Connect to video and control sockets sequentially
                _state.value = ScrcpyState.Connecting("Connecting video stream...")
                var vStream: AdbStream? = null
                for (attempt in 1..15) {
                    delay(250)
                    vStream = runCatching { dadb.open("localabstract:scrcpy") }.getOrNull()
                    if (vStream != null) break
                }
                if (vStream == null) {
                    throw IOException("Could not connect to scrcpy video socket after multiple attempts.")
                }
                videoStream = vStream

                _state.value = ScrcpyState.Connecting("Connecting control stream...")
                val cStream = dadb.open("localabstract:scrcpy")
                controlStream = cStream
                val sink = cStream.sink
                controlSink = sink

                // Start background control worker to process touches and keys off the main thread
                controlJob = scope.launch(Dispatchers.IO) {
                    for (action in controlChannel) {
                        try {
                            action(sink)
                        } catch (e: Exception) {
                            Log.w(TAG, "Control action error", e)
                        }
                    }
                }

                val source: BufferedSource = vStream.source

                // 4. Handshake metadata
                // Dummy byte (when tunnel_forward=true)
                source.readByte()

                // Device Name (64 bytes)
                val devNameBytes = ByteArray(64)
                source.readFully(devNameBytes)
                val deviceName = String(devNameBytes).trim { it <= ' ' || it == '\u0000' }

                // Codec Metadata (12 bytes: 4 bytes codecId, 4 bytes width, 4 bytes height)
                val codecId = source.readInt()
                val w = source.readInt()
                val h = source.readInt()
                videoWidth = if (w > 0) w else 1280
                videoHeight = if (h > 0) h else 720

                Log.d(TAG, "Scrcpy connected! Device: $deviceName, Codec: $codecId, Dimensions: ${videoWidth}x${videoHeight}")

                // 5. Initialize MediaCodec Decoder
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight)
                val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                decoder.configure(format, surface, null, 0)
                decoder.start()
                mediaCodec = decoder
                currentSurface = surface
                isSurfaceValid = surface.isValid

                _state.value = ScrcpyState.Connected(videoWidth, videoHeight, deviceName)

                // 6. Packet Reading & Decoding Loop
                val bufferInfo = MediaCodec.BufferInfo()
                while (isActive && isRunning) {
                    val ptsAndFlags = source.readLong()
                    val isConfig = (ptsAndFlags and (1L shl 62)) != 0L
                    val isKeyFrame = (ptsAndFlags and (1L shl 61)) != 0L
                    val ptsUs = ptsAndFlags and 0x1FFFFFFFFFFFFFFFL
                    val packetSize = source.readInt()

                    val payload = ByteArray(packetSize)
                    source.readFully(payload)

                    // Feed to MediaCodec without dropping frames
                    var inIndex = decoder.dequeueInputBuffer(10_000L)
                    while (inIndex < 0 && isActive && isRunning) {
                        drainOutputBuffers(decoder, bufferInfo, deviceName)
                        inIndex = decoder.dequeueInputBuffer(10_000L)
                    }

                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex)
                        inBuffer?.clear()
                        inBuffer?.put(payload)

                        val flags = when {
                            isConfig -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                            isKeyFrame -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                            else -> 0
                        }
                        try {
                            decoder.queueInputBuffer(inIndex, 0, packetSize, ptsUs, flags)
                        } catch (e: Exception) {
                            Log.w(TAG, "queueInputBuffer skipped", e)
                        }
                    }

                    // Release decoded frames to Surface safely
                    drainOutputBuffers(decoder, bufferInfo, deviceName)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Scrcpy stream error", e)
                    _state.value = ScrcpyState.Error(e.message ?: "Screen mirror disconnected")
                }
            } finally {
                cleanup()
            }
        }
    }

    private fun drainOutputBuffers(decoder: MediaCodec, bufferInfo: MediaCodec.BufferInfo, deviceName: String) {
        var outIndex = decoder.dequeueOutputBuffer(bufferInfo, 0L)
        while (outIndex >= 0 || outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = decoder.outputFormat
                val newW = newFormat.getInteger(MediaFormat.KEY_WIDTH)
                val newH = newFormat.getInteger(MediaFormat.KEY_HEIGHT)
                if (newW > 0 && newH > 0 && (newW != videoWidth || newH != videoHeight)) {
                    Log.d(TAG, "Video format changed dynamically: ${videoWidth}x${videoHeight} -> ${newW}x${newH}")
                    videoWidth = newW
                    videoHeight = newH
                    _state.value = ScrcpyState.Connected(newW, newH, deviceName)
                }
            } else {
                val render = isSurfaceValid && (currentSurface?.isValid == true)
                try {
                    decoder.releaseOutputBuffer(outIndex, render)
                } catch (e: Exception) {
                    Log.w(TAG, "releaseOutputBuffer skipped during surface transition", e)
                }
            }
            outIndex = decoder.dequeueOutputBuffer(bufferInfo, 0L)
        }
    }

    /**
     * Dynamically update the output Surface without restarting the decoder or socket stream.
     */
    fun setSurface(surface: Surface?) {
        currentSurface = surface
        isSurfaceValid = surface?.isValid == true
        if (surface != null && surface.isValid) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mediaCodec?.setOutputSurface(surface)
                }
            }.onFailure {
                Log.w(TAG, "setOutputSurface failed", it)
            }
        }
    }

    /**
     * Send touch event to TV asynchronously on IO thread (safe from NetworkOnMainThreadException)
     */
    fun sendTouchEvent(action: Int, x: Int, y: Int, pressure: Float = 1.0f) {
        if (!isRunning) return
        controlChannel.trySend { sink ->
            ScrcpyProtocol.writeTouchEvent(
                sink = sink,
                action = action,
                pointerId = 0L,
                x = x,
                y = y,
                screenWidth = videoWidth,
                screenHeight = videoHeight,
                pressure = pressure
            )
        }
    }

    /**
     * Send keycode event to TV asynchronously on IO thread
     */
    fun sendKeyCode(keyCode: Int) {
        if (!isRunning) return
        controlChannel.trySend { sink ->
            ScrcpyProtocol.writeKeyCode(sink, ScrcpyProtocol.ACTION_DOWN, keyCode)
            ScrcpyProtocol.writeKeyCode(sink, ScrcpyProtocol.ACTION_UP, keyCode)
        }
    }

    /**
     * Stop mirroring and release all resources cleanly
     */
    fun stop() {
        isRunning = false
        workerJob?.cancel()
        controlJob?.cancel()
        cleanup()
        _state.value = ScrcpyState.Idle
    }

    private fun cleanup() {
        isSurfaceValid = false
        currentSurface = null
        try { videoStream?.close() } catch (_: Exception) {}
        try { controlStream?.close() } catch (_: Exception) {}
        try { shellStream?.close() } catch (_: Exception) {}
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        videoStream = null
        controlStream = null
        controlSink = null
        shellStream = null
        mediaCodec = null
    }
}
