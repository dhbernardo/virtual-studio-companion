package com.vcompanion.desktop.presentation.viewmodel

import app.cash.turbine.test
import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.domain.model.ThermalState
import com.vcompanion.shared.core.ports.IObsConnector
import com.vcompanion.shared.core.ports.IStreamGateway
import com.vcompanion.shared.core.ports.ObsConnectionState
import com.vcompanion.shared.core.protocol.ProtocolMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FakeStreamGateway : IStreamGateway {
    private val _incoming = MutableSharedFlow<ProtocolMessage>(replay = 1, extraBufferCapacity = 64)
    override val incomingMessages: Flow<ProtocolMessage> = _incoming.asSharedFlow()
    val sentMessages = mutableListOf<ProtocolMessage>()
    var isDisconnected = false

    suspend fun emitIncoming(msg: ProtocolMessage) {
        _incoming.emit(msg)
    }

    override suspend fun sendMessage(message: ProtocolMessage): Result<Unit> {
        sentMessages.add(message)
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        isDisconnected = true
    }
}

class FakeObsConnector : IObsConnector {
    private val _connectionState = MutableStateFlow(ObsConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ObsConnectionState> = _connectionState.asStateFlow()
    var lastSetupUrl: String? = null

    fun setState(state: ObsConnectionState) {
        _connectionState.value = state
    }

    override suspend fun connect(host: String, port: Int, password: String?): Result<Unit> {
        _connectionState.value = ObsConnectionState.CONNECTED
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        _connectionState.value = ObsConnectionState.DISCONNECTED
    }

    override suspend fun setupBrowserSource(
        sourceName: String,
        previewUrl: String,
        width: Int,
        height: Int,
        fps: Int
    ): Result<Unit> {
        lastSetupUrl = previewUrl
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopHostViewModelTest {

    @Test
    fun shouldInitializeWithPairingPayloadAnd120sTtl() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals("192.168.1.50", initialState.hostIp)
            assertEquals(8080, initialState.port)
            assertEquals(120, initialState.tokenRemainingSeconds)
            assertTrue(initialState.pairingPayload.startsWith("vcam://pair?"))
            assertTrue(initialState.pairingPayload.contains("host=192.168.1.50"))
            assertTrue(initialState.pairingPayload.contains("port=8080"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shouldCountdownTtlAndRegenerateTokenAtZero() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = testDispatcher
        )

        val initialToken = viewModel.uiState.value.sessionToken

        // Advance 10 seconds
        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()
        assertEquals(110, viewModel.uiState.value.tokenRemainingSeconds)
        assertEquals(initialToken, viewModel.uiState.value.sessionToken)

        // Advance 111 seconds (total 121 seconds)
        testScheduler.advanceTimeBy(111_000)
        testScheduler.runCurrent()
        assertNotEquals(initialToken, viewModel.uiState.value.sessionToken)
    }

    @Test
    fun shouldManuallyRefreshTokenAndResetTtl() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = testDispatcher
        )

        testScheduler.advanceTimeBy(30_000)
        testScheduler.runCurrent()
        assertEquals(90, viewModel.uiState.value.tokenRemainingSeconds)
        val oldToken = viewModel.uiState.value.sessionToken

        viewModel.refreshToken()
        assertEquals(120, viewModel.uiState.value.tokenRemainingSeconds)
        assertNotEquals(oldToken, viewModel.uiState.value.sessionToken)
    }

    @Test
    fun shouldProcessValidHandshakeAndTransitionToConnected() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()
        obs.setState(ObsConnectionState.CONNECTED)

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        val validToken = viewModel.uiState.value.sessionToken

        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = validToken
            )
        )
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Connected)
        assertEquals("Pixel 7 Pro", (viewModel.uiState.value.connectionState as ConnectionState.Connected).sessionInfo)
        assertTrue(gateway.sentMessages.any { it is ProtocolMessage.HandshakeAck })
        assertEquals("http://192.168.1.50:8080/stream/preview", obs.lastSetupUrl)
    }

    @Test
    fun shouldRejectHandshakeWithInvalidToken() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = "invalid-token-value"
            )
        )
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Disconnected)
        assertTrue(gateway.sentMessages.any { it is ProtocolMessage.DisconnectRequest })
    }

    @Test
    fun shouldUpdateTelemetryMetricsAndRttLatency() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        // Receive Telemetry
        gateway.emitIncoming(
            ProtocolMessage.TelemetryPacket(
                timestamp = 1000L,
                metrics = DeviceMetrics(
                    fps = 59.8f,
                    bitrateKbps = 12500L,
                    latencyMs = 24L,
                    batteryLevel = 88,
                    isCharging = true,
                    thermalState = ThermalState.NOMINAL
                )
            )
        )
        testScheduler.runCurrent()

        assertEquals(59.8f, viewModel.uiState.value.fps)
        assertEquals(12500L, viewModel.uiState.value.bitrateKbps)
        assertEquals(88, viewModel.uiState.value.batteryLevel)
        assertTrue(viewModel.uiState.value.isCharging)
        assertEquals(24L, viewModel.uiState.value.rttLatencyMs)
        assertEquals(ConnectionState.Streaming, viewModel.uiState.value.connectionState)
    }

    @Test
    fun shouldDispatchTypedCameraCommandsWhenConnected() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        // Connect first
        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = viewModel.uiState.value.sessionToken
            )
        )
        testScheduler.runCurrent()

        // Send ToggleTorch
        viewModel.sendCommand(CameraCommand.ToggleTorch)
        testScheduler.runCurrent()

        val commandMsg = gateway.sentMessages.filterIsInstance<ProtocolMessage.CommandPacket>().lastOrNull()
        assertEquals(CameraCommand.ToggleTorch, commandMsg?.command)
        assertTrue(viewModel.uiState.value.torchEnabled)

        // Send SetZoom
        viewModel.sendCommand(CameraCommand.SetZoom(2.5f))
        testScheduler.runCurrent()

        val zoomMsg = gateway.sentMessages.filterIsInstance<ProtocolMessage.CommandPacket>().lastOrNull()
        assertEquals(CameraCommand.SetZoom(2.5f), zoomMsg?.command)
        assertEquals(2.5f, viewModel.uiState.value.currentZoom)
    }

    @Test
    fun shouldCleanlyShutdownOnClose() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()
        obs.setState(ObsConnectionState.CONNECTED)

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        // Connect first
        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = viewModel.uiState.value.sessionToken
            )
        )
        testScheduler.runCurrent()

        viewModel.onClose()
        testScheduler.runCurrent()

        assertTrue(gateway.sentMessages.any { it is ProtocolMessage.DisconnectRequest && it.reason == "HOST_SHUTDOWN" })
        assertTrue(gateway.isDisconnected)
        assertEquals(ObsConnectionState.DISCONNECTED, obs.connectionState.value)
    }

    @Test
    fun shouldTransitionToDisconnectedAndRegenerateTokenOnDisconnectSession() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        val initialToken = viewModel.uiState.value.sessionToken

        // Connect first
        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = initialToken
            )
        )
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Connected)

        // Streamer clicks "Desconectar"
        viewModel.disconnectSession()
        testScheduler.runCurrent()

        assertTrue(gateway.sentMessages.any { it is ProtocolMessage.DisconnectRequest && it.reason == "HOST_DISCONNECT" })
        assertEquals(ConnectionState.Disconnected, viewModel.uiState.value.connectionState)
        assertNotEquals(initialToken, viewModel.uiState.value.sessionToken)
        assertEquals(120, viewModel.uiState.value.tokenRemainingSeconds)
    }

    @Test
    fun shouldTransitionToDisconnectedWhenClientDisconnects() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        val initialToken = viewModel.uiState.value.sessionToken

        // Connect first
        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = initialToken
            )
        )
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Connected)

        // Client socket closes or disconnects
        gateway.emitIncoming(ProtocolMessage.DisconnectRequest("CLIENT_CLOSED"))
        testScheduler.runCurrent()

        assertEquals(ConnectionState.Disconnected, viewModel.uiState.value.connectionState)
        assertNotEquals(initialToken, viewModel.uiState.value.sessionToken)
        assertEquals(120, viewModel.uiState.value.tokenRemainingSeconds)
    }

    @Test
    fun shouldUpdateTorchAndZoomWhenReceivingCommandPacketFromClient() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        val initialToken = viewModel.uiState.value.sessionToken
        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = initialToken
            )
        )
        testScheduler.runCurrent()

        // Client toggles torch
        assertEquals(false, viewModel.uiState.value.torchEnabled)
        gateway.emitIncoming(ProtocolMessage.CommandPacket(CameraCommand.ToggleTorch))
        testScheduler.runCurrent()
        assertEquals(true, viewModel.uiState.value.torchEnabled)

        // Client changes zoom
        gateway.emitIncoming(ProtocolMessage.CommandPacket(CameraCommand.SetZoom(2.5f)))
        testScheduler.runCurrent()
        assertEquals(2.5f, viewModel.uiState.value.currentZoom)
    }

    @Test
    fun shouldSetupBrowserSourceWhenObsConnectsDuringActiveSession() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        val initialToken = viewModel.uiState.value.sessionToken
        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = initialToken
            )
        )
        testScheduler.runCurrent()

        // OBS was disconnected, now connects while session is active
        assertEquals(null, obs.lastSetupUrl)
        obs.setState(ObsConnectionState.CONNECTED)
        testScheduler.runCurrent()

        assertEquals("http://192.168.1.50:8080/stream/preview", obs.lastSetupUrl)
    }

    @Test
    fun shouldDisconnectObsIndependentlyWithoutAffectingActiveMobileSession() = runTest {
        val gateway = FakeStreamGateway()
        val obs = FakeObsConnector()

        val viewModel = DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obs,
            hostIp = "192.168.1.50",
            effectivePort = 8080,
            coroutineScope = backgroundScope,
            tickerDispatcher = StandardTestDispatcher(testScheduler)
        )

        val initialToken = viewModel.uiState.value.sessionToken
        gateway.emitIncoming(
            ProtocolMessage.HandshakeInit(
                clientVersion = "1.0.0",
                deviceModel = "Pixel 7 Pro",
                sessionToken = initialToken
            )
        )
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Connected)

        obs.setState(ObsConnectionState.CONNECTED)
        testScheduler.runCurrent()
        assertEquals(ObsConnectionState.CONNECTED, viewModel.uiState.value.obsConnectionState)

        viewModel.disconnectObs()
        testScheduler.runCurrent()

        assertEquals(ObsConnectionState.DISCONNECTED, viewModel.uiState.value.obsConnectionState)
        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Connected)
        assertEquals(0, gateway.sentMessages.filterIsInstance<ProtocolMessage.DisconnectRequest>().size)
    }
}

