package com.vcompanion.android.adapters.telemetry

import android.os.BatteryManager
import android.os.PowerManager
import com.vcompanion.shared.core.domain.model.ThermalState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTelemetryProviderTest {

    @Test
    fun shouldCaptureNominalBatteryAndThermalMetrics() {
        val batteryManager = mockk<BatteryManager>()
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 85
        every { batteryManager.isCharging } returns true

        val powerManager = mockk<PowerManager>()
        every { powerManager.currentThermalStatus } returns PowerManager.THERMAL_STATUS_NONE

        val provider = AndroidTelemetryProvider(
            batteryManager = batteryManager,
            powerManager = powerManager
        )

        val metrics = provider.captureCurrentMetrics()
        assertEquals(85, metrics.batteryLevel)
        assertTrue(metrics.isCharging)
        assertEquals(ThermalState.NOMINAL, metrics.thermalState)
    }

    @Test
    fun shouldMapSevereAndCriticalThermalStatusesCorrectly() {
        val batteryManager = mockk<BatteryManager>()
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 42
        every { batteryManager.isCharging } returns false

        val powerManager = mockk<PowerManager>()
        every { powerManager.currentThermalStatus } returns PowerManager.THERMAL_STATUS_SEVERE

        val providerSevere = AndroidTelemetryProvider(
            batteryManager = batteryManager,
            powerManager = powerManager
        )
        assertEquals(ThermalState.SERIOUS, providerSevere.captureCurrentMetrics().thermalState)

        every { powerManager.currentThermalStatus } returns PowerManager.THERMAL_STATUS_CRITICAL
        val providerCritical = AndroidTelemetryProvider(
            batteryManager = batteryManager,
            powerManager = powerManager
        )
        assertEquals(ThermalState.CRITICAL, providerCritical.captureCurrentMetrics().thermalState)
    }

    @Test
    fun shouldEmitPeriodicMetricsOnFlow() = runTest {
        val batteryManager = mockk<BatteryManager>()
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 90
        every { batteryManager.isCharging } returns false

        val powerManager = mockk<PowerManager>()
        every { powerManager.currentThermalStatus } returns PowerManager.THERMAL_STATUS_NONE

        val provider = AndroidTelemetryProvider(
            batteryManager = batteryManager,
            powerManager = powerManager,
            pollIntervalMs = 50L
        )

        val collected = provider.telemetryFlow.take(2).toList()
        assertEquals(2, collected.size)
        assertEquals(90, collected[0].batteryLevel)
        assertEquals(90, collected[1].batteryLevel)
    }
}
