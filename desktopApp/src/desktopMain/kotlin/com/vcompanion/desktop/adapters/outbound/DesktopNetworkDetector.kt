package com.vcompanion.desktop.adapters.outbound

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Detector de direcciones IP locales en interfaces físicas de Windows.
 * Filtra adaptadores virtuales (WSL, Hyper-V, Docker, VPN, Loopback) cumpliendo con RF-001.
 */
object DesktopNetworkDetector {

    private val VIRTUAL_INTERFACE_PATTERNS = listOf(
        "vethernet",
        "wsl",
        "docker",
        "virtual",
        "vpn",
        "tap",
        "tun",
        "tailscale",
        "loopback",
        "teredo",
        "isatap"
    )

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            val candidates = mutableListOf<String>()

            for (netIf in interfaces.toList()) {
                if (!netIf.isUp || netIf.isLoopback || netIf.isVirtual) continue

                val name = (netIf.name + " " + (netIf.displayName ?: "")).lowercase()
                if (VIRTUAL_INTERFACE_PATTERNS.any { pattern -> name.contains(pattern) }) {
                    continue
                }

                for (inetAddress in netIf.inetAddresses.toList()) {
                    if (inetAddress is Inet4Address && !inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress) {
                        val hostAddress = inetAddress.hostAddress
                        // Prioritize standard local network ranges
                        if (hostAddress.startsWith("192.168.") || hostAddress.startsWith("10.") || hostAddress.startsWith("172.")) {
                            return hostAddress
                        }
                        candidates.add(hostAddress)
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                return candidates.first()
            }
        } catch (_: Exception) {
            // Fallback to localhost if network inspection fails
        }
        return "127.0.0.1"
    }
}
