package com.vcompanion.android.adapters.telemetry

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.domain.model.ThermalState
import com.vcompanion.shared.core.ports.ITelemetryEmitter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class AndroidTelemetryProvider(
    private val batteryManager: BatteryManager? = null,
    private val powerManager: PowerManager? = null,
    private val pollIntervalMs: Long = 500L
) : ITelemetryEmitter {

    constructor(context: Context, pollIntervalMs: Long = 500L) : this(
        batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager,
        powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager,
        pollIntervalMs = pollIntervalMs
    )

    override fun captureCurrentMetrics(): DeviceMetrics {
        val batteryLevel = try {
            val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            level.coerceIn(0, 100)
        } catch (_: Exception) {
            100
        }

        val isCharging = try {
            batteryManager?.isCharging ?: false
        } catch (_: Exception) {
            false
        }

        val thermalState = try {
            val status = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            mapThermalStatus(status)
        } catch (_: Exception) {
            ThermalState.NOMINAL
        }

        return DeviceMetrics(
            fps = 60.0f,
            bitrateKbps = 4000L,
            latencyMs = 0L,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            thermalState = thermalState
        )
    }

    override val telemetryFlow: Flow<DeviceMetrics> = flow {
        while (currentCoroutineContext().isActive) {
            emit(captureCurrentMetrics())
            delay(pollIntervalMs)
        }
    }

    private fun mapThermalStatus(status: Int): ThermalState {
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.NOMINAL
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.FAIR
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SERIOUS
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
            else -> ThermalState.NOMINAL
        }
    }
}
