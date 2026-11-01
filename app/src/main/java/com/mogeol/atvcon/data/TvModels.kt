package com.mogeol.atvcon.data

import android.content.Context
import androidx.annotation.StringRes
import com.mogeol.atvcon.R
import java.util.Locale

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val ip: String, val port: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class TvApp(
    val packageName: String,
    val displayName: String = packageName.substringAfterLast('.'),
    val category: String = "App",
    val iconType: String = "generic"
)

data class TvScreenSize(
    val width: Int = 1920,
    val height: Int = 1080
)

data class TvFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val modifiedTime: String = "",
    val extension: String = ""
) {
    val formattedSize: String
        get() {
            if (isDirectory) return ""
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            val clamped = digitGroups.coerceIn(0, units.size - 1)
            val value = size / Math.pow(1024.0, clamped.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[clamped])
        }
}

enum class RemoteTab(val title: String, @StringRes val titleRes: Int) {
    REMOTE("Remote", R.string.tab_remote),
    SCREEN("Screen", R.string.tab_screen),
    APPS("Apps", R.string.tab_apps),
    FILES("Files", R.string.tab_files)
}

data class MirrorQuality(
    val maxSize: Int = 1280,
    val bitRate: Int = 4_000_000,
    val maxFps: Int = 30
) {
    val resolutionLabel: String
        get() = when {
            maxSize >= 1920 -> "1080p (FHD)"
            maxSize >= 1280 -> "720p (HD)"
            else -> "480p (SD)"
        }

    val bitRateLabel: String
        get() = "${bitRate / 1_000_000} Mbps"

    val summary: String
        get() = "$resolutionLabel • $bitRateLabel • ${maxFps}fps"
}

class MirrorQualityManager(context: Context) {
    private val prefs = context.getSharedPreferences("mirror_quality_prefs", Context.MODE_PRIVATE)

    var quality: MirrorQuality
        get() {
            val size = prefs.getInt("max_size", 1280)
            val bitRate = prefs.getInt("bit_rate", 4_000_000)
            val fps = prefs.getInt("max_fps", 30)
            return MirrorQuality(maxSize = size, bitRate = bitRate, maxFps = fps)
        }
        set(value) {
            prefs.edit()
                .putInt("max_size", value.maxSize)
                .putInt("bit_rate", value.bitRate)
                .putInt("max_fps", value.maxFps)
                .apply()
        }
}

enum class PowerAction(
    val title: String,
    val subtitle: String,
    val command: String,
    val isDestructive: Boolean,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int
) {
    SLEEP(
        title = "Sleep / Standby",
        subtitle = "Toggle standby / screen power",
        command = "input keyevent 26",
        isDestructive = false,
        titleRes = R.string.power_sleep_title,
        subtitleRes = R.string.power_sleep_subtitle
    ),
    RESTART(
        title = "Restart",
        subtitle = "Reboot system normally",
        command = "reboot",
        isDestructive = true,
        titleRes = R.string.power_restart_title,
        subtitleRes = R.string.power_restart_subtitle
    ),
    RECOVERY(
        title = "Restart to Recovery",
        subtitle = "Reboot into recovery console",
        command = "reboot recovery",
        isDestructive = true,
        titleRes = R.string.power_recovery_title,
        subtitleRes = R.string.power_recovery_subtitle
    ),
    FASTBOOT(
        title = "Restart to Fastboot",
        subtitle = "Reboot into bootloader / fastboot",
        command = "reboot bootloader",
        isDestructive = true,
        titleRes = R.string.power_fastboot_title,
        subtitleRes = R.string.power_fastboot_subtitle
    ),
    POWER_OFF(
        title = "Power Off",
        subtitle = "Shut down device completely",
        command = "reboot -p",
        isDestructive = true,
        titleRes = R.string.power_off_title,
        subtitleRes = R.string.power_off_subtitle
    )
}

object AppConstants {
    const val APP_NAME = "Casto"
    const val APP_VERSION = "1.1.0"
    const val GITHUB_REPO_URL = "https://github.com/mogeol/casto"
    const val ALIPAY_QR_URL = "https://qr.alipay.com/fkx15004n444xvwknxa61c5"
    const val ALIPAY_SCHEME_URL = "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2Fqr.alipay.com%2Ffkx15004n444xvwknxa61c5"
    const val PAYPAL_URL = "https://paypal.me/mogeol"
}
