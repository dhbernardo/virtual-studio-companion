package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.protocol.ProtocolMessage

class ProcessTelemetryUseCase {

    fun calculateRttLatency(pingClientTimestamp: Long, pongReceivedTimestamp: Long): Long {
        TODO("Not implemented")
    }

    fun createTelemetryPacket(
        timestamp: Long,
        metrics: DeviceMetrics,
        measuredLatencyMs: Long? = null
    ): ProtocolMessage.TelemetryPacket {
        TODO("Not implemented")
    }
}
