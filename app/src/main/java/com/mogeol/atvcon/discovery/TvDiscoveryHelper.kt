package com.mogeol.atvcon.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

object TvDiscoveryHelper {

    /**
     * Scan local /24 subnet for open ADB port 5555
     */
    suspend fun scanLocalSubnet(
        subnetPrefix: String,
        port: Int = 5555,
        timeoutMs: Int = 250
    ): List<String> = withContext(Dispatchers.IO) {
        val foundIps = Collections.synchronizedList(mutableListOf<String>())
        val jobs = (1..254).map { host ->
            async {
                val targetIp = "$subnetPrefix.$host"
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(targetIp, port), timeoutMs)
                        foundIps.add(targetIp)
                    }
                } catch (_: Exception) {
                    // Port closed / unreachable
                }
            }
        }
        jobs.awaitAll()
        foundIps.sorted()
    }

    /**
     * Get the current device's local IPv4 subnet prefix (e.g. "192.168.1")
     */
    fun getLocalSubnetPrefix(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in Collections.list(interfaces)) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (hostAddress.startsWith("192.168.") || hostAddress.startsWith("10.") || hostAddress.startsWith("172.")) {
                            val parts = hostAddress.split('.')
                            if (parts.size == 4) {
                                return "${parts[0]}.${parts[1]}.${parts[2]}"
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}

