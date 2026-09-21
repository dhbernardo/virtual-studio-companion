package com.vcompanion.shared.core.ports

import com.vcompanion.shared.core.domain.model.DeviceMetrics
import kotlinx.coroutines.flow.Flow

/**
 * Puerto para recolección y emisión de métricas de telemetría de hardware/red.
 * Cumple con Clean Architecture y RNF-001.
 */
interface ITelemetryEmitter {
    val telemetryFlow: Flow<DeviceMetrics>
    fun captureCurrentMetrics(): DeviceMetrics
}
