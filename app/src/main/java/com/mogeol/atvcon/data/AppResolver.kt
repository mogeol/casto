package com.mogeol.atvcon.data

import java.util.Locale

object AppResolver {

    data class AppMetadata(
        val name: String,
        val category: String,
        val iconType: String
    )

    private val KNOWN_APPS = mapOf(
        // Video & Streaming
        "org.smarttube.stable" to AppMetadata("SmartTube", "Streaming Video", "youtube"),
        "org.smarttube.beta" to AppMetadata("SmartTube Beta", "Streaming Video", "youtube"),
        "com.google.android.youtube.tv" to AppMetadata("YouTube TV", "Streaming Video", "youtube"),
        "com.google.android.youtube" to AppMetadata("YouTube", "Streaming Video", "youtube"),
        "com.netflix.ninja" to AppMetadata("Netflix", "Streaming Video", "video"),
        "com.amazon.amazonvideo.livingroom" to AppMetadata("Prime Video", "Streaming Video", "video"),
        "com.disney.disneyplus" to AppMetadata("Disney+", "Streaming Video", "video"),
        "com.apple.atve.androidtv.appletv" to AppMetadata("Apple TV", "Streaming Video", "video"),
        "tv.emby.embyatv" to AppMetadata("Emby for Android TV", "Media Server", "video"),
        "org.jellyfin.androidtv" to AppMetadata("Jellyfin TV", "Media Server", "video"),
        "com.plexapp.android" to AppMetadata("Plex", "Media Server", "video"),
        "org.xbmc.kodi" to AppMetadata("Kodi", "Media Center", "video"),
        "org.videolan.vlc" to AppMetadata("VLC Media Player", "Video Player", "vlc"),
        "com.mxtech.videoplayer.pro" to AppMetadata("MX Player Pro", "Video Player", "video"),
        "com.mxtech.videoplayer.ad" to AppMetadata("MX Player", "Video Player", "video"),
        "tv.danmaku.bili" to AppMetadata("Bilibili", "Streaming Video", "video"),
        "com.bilibili.tv" to AppMetadata("Bilibili TV (云视听小电视)", "Streaming Video", "video"),
        "com.iqiyi.tv" to AppMetadata("爱奇艺 (银河奇异果)", "Streaming Video", "video"),
        "com.ktcp.tvvideo" to AppMetadata("腾讯视频 (云视听极光)", "Streaming Video", "video"),
        "com.youku.tv" to AppMetadata("优酷 (酷喵)", "Streaming Video", "video"),

        // Launchers & System
        "com.ss.squarehome2" to AppMetadata("Square Home", "TV Launcher", "launcher"),
        "com.google.android.tvlauncher" to AppMetadata("Android TV Home", "System Launcher", "launcher"),
        "com.droidlogic.tv.settings" to AppMetadata("TV Settings", "System Settings", "settings"),
        "com.android.settings" to AppMetadata("Android Settings", "System Settings", "settings"),
        "com.droidlogic.mediacenter" to AppMetadata("Media Center", "Local Media", "video"),
        "com.android.gallery3d" to AppMetadata("Gallery", "Photos & Media", "image"),
        "com.android.music" to AppMetadata("Music Player", "Audio Player", "music"),
        "com.google.android.inputmethod.latin" to AppMetadata("Gboard", "Input Method", "keyboard"),

        // Tools, Utilities & File Managers
        "pl.solidexplorer2" to AppMetadata("Solid Explorer", "File Manager", "folder"),
        "com.speedsoftware.rootexplorer" to AppMetadata("Root Explorer", "File Manager", "folder"),
        "com.termux" to AppMetadata("Termux", "Terminal Emulator", "terminal"),
        "com.tailscale.ipn" to AppMetadata("Tailscale", "VPN / Mesh", "network"),
        "moe.nb4a" to AppMetadata("NekoBox", "Proxy / VPN", "network"),
        "org.adaway" to AppMetadata("AdAway", "Ad Blocker", "shield"),
        "com.coolapk.market" to AppMetadata("酷安 (Coolapk)", "App Market", "store"),
        "com.xunlei.downloadprovider.hd" to AppMetadata("迅雷 HD (Xunlei)", "Downloader", "download"),
        "idm.internet.download.manager.plus" to AppMetadata("1DM+ Downloader", "Downloader", "download"),
        "com.dangbei.screencast" to AppMetadata("当贝投屏 (Dangbei)", "Screen Cast", "cast"),
        "com.pico.screencast" to AppMetadata("Pico Cast", "Screen Cast", "cast"),
        "com.microsoft.rdc.android" to AppMetadata("Remote Desktop", "Remote Access", "remote"),
        "com.vivaldi.browser" to AppMetadata("Vivaldi Browser", "Web Browser", "browser"),
        "com.xbrowser.play" to AppMetadata("XBrowser", "Web Browser", "browser"),
        "com.android.chrome" to AppMetadata("Chrome", "Web Browser", "browser"),
        "com.ttxapps.wifiadb" to AppMetadata("WiFi ADB", "Developer Tool", "terminal"),
        "com.absinthe.anywhere_" to AppMetadata("Anywhere-", "Shortcuts & Tools", "tools"),
        "me.yourbay.airfrozen" to AppMetadata("AirFrozen", "App Freezer", "shield"),
        "com.elsdoerfer.android.autostarts" to AppMetadata("Autostarts", "Boot Manager", "tools"),
        "com.foxit.mobile.pdf.lite" to AppMetadata("Foxit PDF", "Document Viewer", "document"),
        "com.azefsw.audioconnect" to AppMetadata("Audio Connect", "Audio Streamer", "music"),
        "com.fusionmedia.investing" to AppMetadata("Investing.com", "Financial Markets", "finance"),
        "com.edu.todo" to AppMetadata("To-Do Task", "Productivity", "todo"),
        "com.chinasoul.bt" to AppMetadata("Bluetooth Manager", "System Tool", "bluetooth"),
        "dipla.studio.randomslideshow" to AppMetadata("Photo Slideshow", "Photos & Media", "image")
    )

    fun resolve(packageName: String): TvApp {
        val trimmed = packageName.trim()
        val known = KNOWN_APPS[trimmed]
        if (known != null) {
            return TvApp(
                packageName = trimmed,
                displayName = known.name,
                category = known.category,
                iconType = known.iconType
            )
        }

        // Dynamic heuristic name extraction
        val segments = trimmed.split('.').filter { it.isNotEmpty() }
        val rawName = if (segments.size >= 2) {
            val last = segments.last()
            val secondLast = segments[segments.size - 2]
            val skipLast = setOf("android", "app", "stable", "beta", "lite", "pro", "hd", "tv", "play", "plus", "free")
            if (skipLast.contains(last.lowercase(Locale.US)) && secondLast.length > 2) {
                "${formatSegment(secondLast)} ${formatSegment(last)}"
            } else {
                formatSegment(last)
            }
        } else {
            formatSegment(trimmed)
        }

        // Determine icon category
        val lower = trimmed.lowercase(Locale.US)
        val iconType = when {
            lower.contains("video") || lower.contains("player") || lower.contains("cinema") || lower.contains("tv") -> "video"
            lower.contains("music") || lower.contains("audio") || lower.contains("sound") -> "music"
            lower.contains("browser") || lower.contains("web") -> "browser"
            lower.contains("file") || lower.contains("explorer") || lower.contains("storage") -> "folder"
            lower.contains("terminal") || lower.contains("cmd") || lower.contains("shell") -> "terminal"
            lower.contains("cast") || lower.contains("miracast") || lower.contains("airplay") -> "cast"
            lower.contains("down") || lower.contains("torrent") || lower.contains("idm") -> "download"
            lower.contains("setting") || lower.contains("config") -> "settings"
            lower.contains("vpn") || lower.contains("proxy") || lower.contains("tailscale") -> "network"
            else -> "generic"
        }

        val category = when (iconType) {
            "video" -> "Video & Media"
            "music" -> "Audio & Music"
            "browser" -> "Web Browser"
            "folder" -> "File Management"
            "terminal" -> "Tools & Terminal"
            "cast" -> "Screen Cast"
            "download" -> "Downloader"
            "settings" -> "System"
            "network" -> "Network / VPN"
            else -> "Application"
        }

        return TvApp(
            packageName = trimmed,
            displayName = rawName,
            category = category,
            iconType = iconType
        )
    }

    private fun formatSegment(s: String): String {
        val withSpaces = s.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ')
        return withSpaces.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
    }
}
