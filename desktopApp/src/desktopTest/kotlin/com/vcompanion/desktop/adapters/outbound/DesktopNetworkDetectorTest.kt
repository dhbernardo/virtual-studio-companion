package com.vcompanion.desktop.adapters.outbound

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopNetworkDetectorTest {

    @Test
    fun shouldReturnValidIpAddress() {
        val ip = DesktopNetworkDetector.getLocalIpAddress()
        assertNotNull(ip)
        assertTrue(ip.isNotBlank(), "IP should not be blank")
        // Basic IPv4 format validation
        val parts = ip.split(".")
        assertTrue(parts.size == 4, "IP should have 4 octets: $ip")
        assertTrue(parts.all { it.toIntOrNull() in 0..255 }, "Each octet should be 0..255: $ip")
    }
}
