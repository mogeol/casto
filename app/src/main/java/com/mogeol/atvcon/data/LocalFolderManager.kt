package com.mogeol.atvcon.data

import android.content.Context
import android.content.Intent
import android.net.Uri

class LocalFolderManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("local_folder_prefs", Context.MODE_PRIVATE)

    var treeUri: Uri?
        get() {
            val uriStr = prefs.getString(KEY_FOLDER_URI, null) ?: return null
            return runCatching { Uri.parse(uriStr) }.getOrNull()
        }
        set(value) {
            if (value != null) {
                prefs.edit().putString(KEY_FOLDER_URI, value.toString()).apply()
            } else {
                prefs.edit().remove(KEY_FOLDER_URI).apply()
            }
        }

    val displayPath: String
        get() {
            val uri = treeUri ?: return "Download/Casto"
            return formatTreeUri(uri)
        }

    fun setFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        treeUri = uri
    }

    fun resetToDefault() {
        treeUri = null
    }

    companion object {
        private const val KEY_FOLDER_URI = "folder_tree_uri"

        fun formatTreeUri(uri: Uri): String {
            val path = uri.path ?: return "Custom Folder"
            val decoded = Uri.decode(path)
            return when {
                decoded.contains("primary:") -> decoded.substringAfter("primary:")
                decoded.contains(":") -> decoded.substringAfterLast(":")
                else -> decoded.substringAfterLast("/")
            }
        }
    }
}
