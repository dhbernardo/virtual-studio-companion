package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.protocol.ProtocolMessage

/**
 * Procesa paquetes de telemetría y calcula latencia RTT basada en Ping/Pong.
 * Cumple con RF-003, RNF-001 y RNF-002 (inmutable, sin dependencias de plataforma).
 */
class ProcessTelemetryUseCase {

    fun calculateRttLatency(pingClientTimestamp: Long, pongReceivedTimestamp: Long): Long {
        return (pongReceivedTimestamp - pingClientTimestamp).coerceAtLeast(0L)
    }

    fun createTelemetryPacket(
        timestamp: Long,
        metrics: DeviceMetrics,
        measuredLatencyMs: Long? = null
    ): ProtocolMessage.TelemetryPacket {
        val updatedMetrics = if (measuredLatencyMs != null) {
            metrics.copy(latencyMs = measuredLatencyMs)
        } else {
            metrics
        }
        return ProtocolMessage.TelemetryPacket(
            timestamp = timestamp,
            metrics = updatedMetrics
        )
    }
}
