package com.mogeol.atvcon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mogeol.atvcon.adb.TvRemoteManager
import com.mogeol.atvcon.ui.RemoteScreen
import com.mogeol.atvcon.ui.theme.AdbTvRemoteTheme

class MainActivity : ComponentActivity() {

    private lateinit var remoteManager: TvRemoteManager
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize ADB Remote Manager using internal filesDir for secure key persistence
        remoteManager = TvRemoteManager(filesDir)

        handleIntent(intent)

        setContent {
            AdbTvRemoteTheme {
                RemoteScreen(
                    remoteManager = remoteManager,
                    pendingUrl = sharedUrl,
                    onClearPendingUrl = { sharedUrl = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    sharedUrl = extractUrlOrText(text)
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                if (!dataUri.isNullOrBlank()) {
                    sharedUrl = dataUri
                }
            }
        }
    }

    private fun extractUrlOrText(raw: String): String {
        val trimmed = raw.trim()
        val urlRegex = Regex("""https?://[^\s]+""")
        val match = urlRegex.find(trimmed)
        return match?.value ?: trimmed
    }

    override fun onDestroy() {
        super.onDestroy()
        remoteManager.disconnect()
    }
}
