package com.vcompanion.shared.core.protocol

import com.vcompanion.shared.core.domain.model.CameraCapabilities
import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.CommandDispatchResult
import com.vcompanion.shared.core.domain.model.CommandErrorCode
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.domain.model.LensFacing
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.core.domain.model.ThermalState
import com.vcompanion.shared.core.domain.usecase.CameraCommandDispatcher
import com.vcompanion.shared.core.domain.usecase.ProcessTelemetryUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T05: Pruebas unitarias para serialización de ProtocolMessage y comandos remotos.
 * Valida RF-003, RF-004 y RNF-003.
 */
class ProtocolSerializationAndCommandTest {

    private val telemetryUseCase = ProcessTelemetryUseCase()
    private val commandDispatcher = CameraCommandDispatcher(
        capabilities = CameraCapabilities(
            minZoom = 1.0f,
            maxZoom = 5.0f,
            hasTorch = true,
            supportedLensFacing = setOf(LensFacing.BACK, LensFacing.FRONT)
        )
    )

    @Test
    fun shouldSerializeAndDeserializeHandshakeMessages() {
        val init = ProtocolMessage.HandshakeInit(
            clientVersion = "1.0.0",
            deviceModel = "Pixel 8 Pro",
            sessionToken = "tok_abc_123"
        )
        val jsonInit = CoreJson.encodeToString(ProtocolMessage.serializer(), init)
        val decodedInit = CoreJson.decodeFromString(ProtocolMessage.serializer(), jsonInit)

        assertEquals(init, decodedInit)
        assertTrue(jsonInit.contains("\"type\":\"HANDSHAKE_INIT\""))

        val ack = ProtocolMessage.HandshakeAck(
            serverVersion = "1.0.0",
            approvedFps = 60,
            approvedResolution = "1080p"
        )
        val jsonAck = CoreJson.encodeToString(ProtocolMessage.serializer(), ack)
        val decodedAck = CoreJson.decodeFromString(ProtocolMessage.serializer(), jsonAck)

        assertEquals(ack, decodedAck)
        assertTrue(jsonAck.contains("\"type\":\"HANDSHAKE_ACK\""))
    }

    @Test
    fun shouldSerializeAndDeserializeTelemetryPacketMatchingRf003Schema() {
        val metrics = DeviceMetrics(
            fps = 60.0f,
            bitrateKbps = 8500L,
            latencyMs = 42L,
            batteryLevel = 85,
            isCharging = false,
            thermalState = ThermalState.NOMINAL
        )
        val packet = ProtocolMessage.TelemetryPacket(
            timestamp = 1726850000000L,
            metrics = metrics
        )

        val json = CoreJson.encodeToString(ProtocolMessage.serializer(), packet)
        val decoded = CoreJson.decodeFromString(ProtocolMessage.serializer(), json)

        assertEquals(packet, decoded)
        assertTrue(json.contains("\"type\":\"TELEMETRY\""))
        assertTrue(json.contains("\"fps\":60"))
        assertTrue(json.contains("\"bitrateKbps\":8500"))
        assertTrue(json.contains("\"latencyMs\":42"))
        assertTrue(json.contains("\"batteryLevel\":85"))
        assertTrue(json.contains("\"thermalState\":\"NOMINAL\""))
    }

    @Test
    fun shouldSerializeAndDeserializePingPongAndDisconnectMessages() {
        val ping = ProtocolMessage.Ping(clientTimestamp = 1726850000100L)
        val pingJson = CoreJson.encodeToString(ProtocolMessage.serializer(), ping)
        val decodedPing = CoreJson.decodeFromString(ProtocolMessage.serializer(), pingJson)
        assertEquals(ping, decodedPing)

        val pong = ProtocolMessage.Pong(clientTimestamp = 1726850000100L)
        val pongJson = CoreJson.encodeToString(ProtocolMessage.serializer(), pong)
        val decodedPong = CoreJson.decodeFromString(ProtocolMessage.serializer(), pongJson)
        assertEquals(pong, decodedPong)

        val disconnect = ProtocolMessage.DisconnectRequest(reason = "USER_REQUEST")
        val disconnectJson = CoreJson.encodeToString(ProtocolMessage.serializer(), disconnect)
        val decodedDisconnect = CoreJson.decodeFromString(ProtocolMessage.serializer(), disconnectJson)
        assertEquals(disconnect, decodedDisconnect)
    }

    @Test
    fun shouldSerializeAndDeserializeCommandPackets() {
        val zoomCmd = ProtocolMessage.CommandPacket(CameraCommand.SetZoom(2.5f))
        val zoomJson = CoreJson.encodeToString(ProtocolMessage.serializer(), zoomCmd)
        val decodedZoom = CoreJson.decodeFromString(ProtocolMessage.serializer(), zoomJson)
        assertEquals(zoomCmd, decodedZoom)

        val torchCmd = ProtocolMessage.CommandPacket(CameraCommand.ToggleTorch)
        val torchJson = CoreJson.encodeToString(ProtocolMessage.serializer(), torchCmd)
        val decodedTorch = CoreJson.decodeFromString(ProtocolMessage.serializer(), torchJson)
        assertEquals(torchCmd, decodedTorch)
    }

    @Test
    fun shouldCalculateRttLatencyCorrectly() {
        val pingSent = 1_000_000L
        val pongReceived = 1_000_042L
        val latency = telemetryUseCase.calculateRttLatency(pingSent, pongReceived)
        assertEquals(42L, latency)

        // Edge case: Clock drift or negative difference coerced to 0
        val negativeLatency = telemetryUseCase.calculateRttLatency(1_000_050L, 1_000_040L)
        assertEquals(0L, negativeLatency)
    }

    @Test
    fun shouldCreateTelemetryPacketWithInjectedLatency() {
        val baseMetrics = DeviceMetrics(
            fps = 60.0f,
            bitrateKbps = 6000L,
            latencyMs = 0L,
            batteryLevel = 90,
            isCharging = true,
            thermalState = ThermalState.NOMINAL
        )

        val packet = telemetryUseCase.createTelemetryPacket(
            timestamp = 1_700_000_000L,
            metrics = baseMetrics,
            measuredLatencyMs = 38L
        )

        assertEquals(38L, packet.metrics.latencyMs)
        assertEquals(1_700_000_000L, packet.timestamp)
    }

    @Test
    fun shouldDispatchValidZoomCommandDuringStreaming() {
        val command = CameraCommand.SetZoom(2.5f)
        val result = commandDispatcher.dispatch(command, ConnectionState.Streaming)

        assertTrue(result.isSuccess)
        assertIs<CommandDispatchResult.Success>(result)
        assertEquals(command, result.command)
    }

    @Test
    fun shouldRejectZoomCommandWhenOutOfRange() {
        val tooLow = CameraCommand.SetZoom(0.5f) // < min 1.0
        val resultLow = commandDispatcher.dispatch(tooLow, ConnectionState.Streaming)
        assertFalse(resultLow.isSuccess)
        assertIs<CommandDispatchResult.Rejected>(resultLow)
        assertEquals(CommandErrorCode.VALUE_OUT_OF_RANGE, resultLow.errorCode)

        val tooHigh = CameraCommand.SetZoom(8.0f) // > max 5.0
        val resultHigh = commandDispatcher.dispatch(tooHigh, ConnectionState.Streaming)
        assertFalse(resultHigh.isSuccess)
        assertIs<CommandDispatchResult.Rejected>(resultHigh)
        assertEquals(CommandErrorCode.VALUE_OUT_OF_RANGE, resultHigh.errorCode)
    }

    @Test
    fun shouldRejectTorchCommandWhenUnsupported() {
        val noTorchDispatcher = CameraCommandDispatcher(
            capabilities = CameraCapabilities(hasTorch = false)
        )
        val result = noTorchDispatcher.dispatch(CameraCommand.ToggleTorch, ConnectionState.Streaming)

        assertFalse(result.isSuccess)
        assertIs<CommandDispatchResult.Rejected>(result)
        assertEquals(CommandErrorCode.UNSUPPORTED_HARDWARE, result.errorCode)
    }

    @Test
    fun shouldRejectCommandWhenConnectionStateIsInvalid() {
        val command = CameraCommand.SetZoom(2.0f)

        val disconnectedResult = commandDispatcher.dispatch(command, ConnectionState.Disconnected)
        assertFalse(disconnectedResult.isSuccess)
        assertEquals(CommandErrorCode.INVALID_STATE, (disconnectedResult as CommandDispatchResult.Rejected).errorCode)

        val pairingResult = commandDispatcher.dispatch(
            command,
            ConnectionState.Pairing(PairingConfig("192.168.1.1", 8080, "tok"))
        )
        assertFalse(pairingResult.isSuccess)
        assertEquals(CommandErrorCode.INVALID_STATE, (pairingResult as CommandDispatchResult.Rejected).errorCode)
    }

    @Test
    fun shouldValidateSwitchLensCommand() {
        val validBack = CameraCommand.SwitchLens(LensFacing.BACK.name)
        val resultBack = commandDispatcher.dispatch(validBack, ConnectionState.Streaming)
        assertTrue(resultBack.isSuccess)

        val invalidLensDispatcher = CameraCommandDispatcher(
            capabilities = CameraCapabilities(
                supportedLensFacing = setOf(LensFacing.BACK) // No FRONT
            )
        )
        val invalidFront = CameraCommand.SwitchLens(LensFacing.FRONT.name)
        val resultFront = invalidLensDispatcher.dispatch(invalidFront, ConnectionState.Streaming)
        assertFalse(resultFront.isSuccess)
        assertEquals(CommandErrorCode.UNSUPPORTED_HARDWARE, (resultFront as CommandDispatchResult.Rejected).errorCode)
    }
}
