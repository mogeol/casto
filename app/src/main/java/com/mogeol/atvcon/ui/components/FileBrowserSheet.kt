package com.mogeol.atvcon.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mogeol.atvcon.adb.TvRemoteManager
import com.mogeol.atvcon.data.TvFileItem
import com.mogeol.atvcon.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserSheet(
    remoteManager: TvRemoteManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf("/sdcard") }
    var fileList by remember { mutableStateOf<List<TvFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf<String?>(null) }
    var selectedFileItem by remember { mutableStateOf<TvFileItem?>(null) }
    var showApkInstallPrompt by remember { mutableStateOf<String?>(null) }

    val loadDirectory: (String) -> Unit = { path ->
        coroutineScope.launch {
            isLoading = true
            remoteManager.listFiles(path).onSuccess { items ->
                currentPath = path
                fileList = items
            }.onFailure { err ->
                Toast.makeText(context, "Error reading $path: ${err.message}", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentPath) {
        loadDirectory(currentPath)
    }

    // File picker launcher for uploading files from Phone to TV
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val displayName = getFileNameFromUri(context, uri) ?: "upload_${System.currentTimeMillis()}"
                    transferStatus = "Uploading $displayName to TV..."
                    val tempCacheFile = File(context.cacheDir, displayName)

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempCacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val remoteDest = if (currentPath.endsWith("/")) "$currentPath$displayName" else "$currentPath/$displayName"
                    remoteManager.pushFile(tempCacheFile, remoteDest).onSuccess {
                        transferStatus = null
                        Toast.makeText(context, "Uploaded to $remoteDest", Toast.LENGTH_SHORT).show()
                        loadDirectory(currentPath)

                        if (displayName.lowercase().endsWith(".apk")) {
                            showApkInstallPrompt = remoteDest
                        }
                    }.onFailure { err ->
                        transferStatus = null
                        Toast.makeText(context, "Upload failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }

                    tempCacheFile.delete()
                } catch (e: Exception) {
                    transferStatus = null
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch("*/*") },
                containerColor = AccentCyan,
                contentColor = Color.Black,
                icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                text = { Text("Upload to TV", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 2.dp)
        ) {
            // Path Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val canGoUp = currentPath != "/" && currentPath != "/sdcard"
                    IconButton(
                        onClick = {
                            val parent = getParentPath(currentPath)
                            loadDirectory(parent)
                        },
                        enabled = canGoUp
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Up directory",
                            tint = if (canGoUp) AccentCyan else Color.DarkGray
                        )
                    }

                    Text(
                        text = currentPath,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { loadDirectory(currentPath) }, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentCyan)
                }
            }

            // Quick Shortcut Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Home" to "/sdcard",
                    "Download" to "/sdcard/Download",
                    "Movies" to "/sdcard/Movies",
                    "Pictures" to "/sdcard/Pictures",
                    "DCIM" to "/sdcard/DCIM"
                ).forEach { (label, path) ->
                    val isSelected = currentPath == path
                    FilterChip(
                        selected = isSelected,
                        onClick = { loadDirectory(path) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceVariant,
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transfer Status Banner
            val status = transferStatus
            if (status != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentCyan)
                        Text(status, color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // File Listing
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            } else if (fileList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Directory is empty.\nTap + Upload to transfer files to TV.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(fileList, key = { it.path }) { item ->
                        FileItemRow(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    loadDirectory(item.path)
                                } else {
                                    selectedFileItem = item
                                }
                            },
                            onMoreClick = {
                                selectedFileItem = item
                            }
                        )
                    }
                }
            }
        }
    }

    // File Action Dialog (Download, Install, Open, Delete)
    val selectedItem = selectedFileItem
    if (selectedItem != null) {
        AlertDialog(
            onDismissRequest = { selectedFileItem = null },
            title = {
                Text(
                    text = selectedItem.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Path: ${selectedItem.path}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    if (!selectedItem.isDirectory) {
                        Text(
                            text = "Size: ${selectedItem.formattedSize} • ${selectedItem.modifiedTime}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Download / Pull to phone
                    if (!selectedItem.isDirectory) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val item = selectedItem
                                    selectedFileItem = null
                                    coroutineScope.launch {
                                        transferStatus = "Downloading ${item.name} from TV..."
                                        val destDir = File(context.cacheDir, "downloads").apply { mkdirs() }
                                        val destFile = File(destDir, item.name)

                                        remoteManager.pullFile(item.path, destFile).onSuccess { downloaded ->
                                            transferStatus = null
                                            Toast.makeText(context, "Downloaded ${item.name}", Toast.LENGTH_SHORT).show()
                                            shareDownloadedFile(context, downloaded)
                                        }.onFailure { err ->
                                            transferStatus = null
                                            Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = AccentCyan)
                                Column {
                                    Text("Pull to Phone", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
                                    Text("Download file to your local phone storage", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    // Install on TV (for APKs)
                    if (selectedItem.extension == "apk") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val item = selectedItem
                                    selectedFileItem = null
                                    coroutineScope.launch {
                                        transferStatus = "Installing ${item.name} on TV..."
                                        remoteManager.installRemoteApk(item.path).onSuccess {
                                            transferStatus = null
                                            Toast.makeText(context, "Installed ${item.name} successfully!", Toast.LENGTH_LONG).show()
                                        }.onFailure { err ->
                                            transferStatus = null
                                            Toast.makeText(context, "Install failed: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Android, contentDescription = null, tint = AccentGreen)
                                Column {
                                    Text("Install APK on TV", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
                                    Text("Direct 1-tap package installation on Android TV", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    // Open / Launch on TV
                    if (!selectedItem.isDirectory) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val item = selectedItem
                                    selectedFileItem = null
                                    coroutineScope.launch {
                                        remoteManager.openRemoteFile(item.path).onSuccess {
                                            Toast.makeText(context, "Opened on TV", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = null, tint = Color.White)
                                Column {
                                    Text("Open on TV", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
                                    Text("Trigger TV default player or viewer for this file", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    // Delete from TV
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val item = selectedItem
                                selectedFileItem = null
                                coroutineScope.launch {
                                    remoteManager.deleteRemoteFile(item.path).onSuccess {
                                        Toast.makeText(context, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                                        loadDirectory(currentPath)
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Delete failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed)
                            Column {
                                Text("Delete from TV", fontWeight = FontWeight.SemiBold, color = AccentRed, fontSize = 13.sp)
                                Text("Permanently delete file or directory from TV storage", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedFileItem = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Prompt to install uploaded APK
    val apkToInstall = showApkInstallPrompt
    if (apkToInstall != null) {
        AlertDialog(
            onDismissRequest = { showApkInstallPrompt = null },
            title = { Text("Install Uploaded APK?") },
            text = { Text("The APK has been pushed to the TV ($apkToInstall). Would you like to install it on the TV now?") },
            confirmButton = {
                Button(
                    onClick = {
                        val path = apkToInstall
                        showApkInstallPrompt = null
                        coroutineScope.launch {
                            transferStatus = "Installing APK on TV..."
                            remoteManager.installRemoteApk(path).onSuccess {
                                transferStatus = null
                                Toast.makeText(context, "APK Installed Successfully!", Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                transferStatus = null
                                Toast.makeText(context, "Install failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                ) {
                    Text("Install Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApkInstallPrompt = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun FileItemRow(
    item: TvFileItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = getFileIcon(item),
                    contentDescription = null,
                    tint = getFileIconColor(item),
                    modifier = Modifier.size(24.dp)
                )

                Column {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.isDirectory) {
                        Text(
                            text = "${item.formattedSize} • ${item.modifiedTime}",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            IconButton(onClick = onMoreClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
            }
        }
    }
}

private fun getFileIcon(item: TvFileItem): ImageVector {
    if (item.isDirectory) return Icons.Default.Folder
    return when (item.extension) {
        "apk" -> Icons.Default.Android
        "mp4", "mkv", "avi", "mov", "webm", "ts" -> Icons.Default.Movie
        "mp3", "flac", "wav", "aac", "ogg" -> Icons.Default.MusicNote
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "pdf", "txt", "doc", "docx" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileIconColor(item: TvFileItem): Color {
    if (item.isDirectory) return AccentCyan
    return when (item.extension) {
        "apk" -> AccentGreen
        "mp4", "mkv", "avi", "mov", "webm", "ts" -> Color(0xFFFFB74D) // Amber/Orange
        "mp3", "flac", "wav", "aac", "ogg" -> Color(0xFFBA68C8)       // Purple
        "jpg", "jpeg", "png", "webp", "gif" -> Color(0xFF4FC3F7)     // Light Blue
        else -> TextSecondary
    }
}

private fun getParentPath(path: String): String {
    val trimmed = path.trimEnd('/')
    val lastSlash = trimmed.lastIndexOf('/')
    return if (lastSlash <= 0) "/" else trimmed.substring(0, lastSlash)
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = cursor.getString(index)
                }
            }
        }
    }
    return name ?: uri.lastPathSegment
}

private fun shareDownloadedFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Downloaded File"))
    } catch (_: Exception) {}
}
