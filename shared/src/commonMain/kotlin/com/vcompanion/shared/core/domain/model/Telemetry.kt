package com.vcompanion.shared.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThermalState {
    NOMINAL,
    FAIR,
    SERIOUS,
    CRITICAL
}

@Serializable
data class DeviceMetrics(
    val fps: Float,
    val bitrateKbps: Long,
    val latencyMs: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val thermalState: ThermalState = ThermalState.NOMINAL
)
