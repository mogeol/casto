package com.mogeol.atvcon.data

import android.content.Context
import android.content.SharedPreferences

class HostManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastConnectedHost: String
        get() = prefs.getString(KEY_LAST_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) {
            prefs.edit().putString(KEY_LAST_HOST, value).apply()
            addHostToHistory(value)
        }

    fun getHistoryHosts(): List<String> {
        val raw = prefs.getString(KEY_HISTORY_HOSTS, "") ?: ""
        if (raw.isBlank()) return listOf(lastConnectedHost)
        val list = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (!list.contains(lastConnectedHost)) {
            list.add(0, lastConnectedHost)
        }
        return list.distinct()
    }

    fun addHostToHistory(host: String) {
        val trimmed = host.trim()
        if (trimmed.isBlank()) return
        val current = getHistoryHosts().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val saved = current.take(10).joinToString(",")
        prefs.edit().putString(KEY_HISTORY_HOSTS, saved).apply()
    }

    fun removeHostFromHistory(host: String) {
        val current = getHistoryHosts().toMutableList()
        current.remove(host.trim())
        val saved = current.joinToString(",")
        prefs.edit().putString(KEY_HISTORY_HOSTS, saved).apply()
    }

    companion object {
        private const val PREFS_NAME = "atvcon_hosts_prefs"
        private const val KEY_LAST_HOST = "last_connected_host"
        private const val KEY_HISTORY_HOSTS = "history_hosts_list"
        private const val DEFAULT_HOST = "192.168.1.10"
    }
}
