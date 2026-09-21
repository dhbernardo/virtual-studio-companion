package com.vcompanion.shared.core.protocol

import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ProtocolMessage {

    @Serializable
    @SerialName("HANDSHAKE_INIT")
    data class HandshakeInit(
        val clientVersion: String,
        val deviceModel: String,
        val sessionToken: String
    ) : ProtocolMessage()

    @Serializable
    @SerialName("HANDSHAKE_ACK")
    data class HandshakeAck(
        val serverVersion: String,
        val approvedFps: Int = 60,
        val approvedResolution: String = "1080p"
    ) : ProtocolMessage()

    @Serializable
    @SerialName("TELEMETRY")
    data class TelemetryPacket(
        val timestamp: Long,
        val metrics: DeviceMetrics
    ) : ProtocolMessage()

    @Serializable
    @SerialName("COMMAND")
    data class CommandPacket(
        val command: CameraCommand
    ) : ProtocolMessage()

    @Serializable
    @SerialName("PING")
    data class Ping(val clientTimestamp: Long) : ProtocolMessage()

    @Serializable
    @SerialName("PONG")
    data class Pong(val clientTimestamp: Long) : ProtocolMessage()

    @Serializable
    @SerialName("DISCONNECT_REQUEST")
    data class DisconnectRequest(val reason: String = "USER_REQUEST") : ProtocolMessage()
}
