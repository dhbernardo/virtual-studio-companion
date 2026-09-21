package com.vcompanion.desktop.presentation.viewmodel

import com.vcompanion.desktop.adapters.outbound.DesktopNetworkDetector
import com.vcompanion.shared.core.domain.model.CameraCapabilities
import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.CommandDispatchResult
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.domain.model.SessionTokenMetadata
import com.vcompanion.shared.core.domain.usecase.CameraCommandDispatcher
import com.vcompanion.shared.core.domain.usecase.GeneratePairingPayloadUseCase
import com.vcompanion.shared.core.domain.usecase.ValidateSessionTokenUseCase
import com.vcompanion.shared.core.ports.IObsConnector
import com.vcompanion.shared.core.ports.IStreamGateway
import com.vcompanion.shared.core.ports.ObsConnectionState
import com.vcompanion.shared.core.protocol.ProtocolMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Estado UI reactivo para la ventana principal del Host de escritorio.
 * Cumple con RF-002, RF-004 y RNF-004.
 */
data class DesktopHostUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val obsConnectionState: ObsConnectionState = ObsConnectionState.DISCONNECTED,
    val hostIp: String = "127.0.0.1",
    val port: Int = 8080,
    val sessionToken: String = "",
    val pairingPayload: String = "",
    val tokenRemainingSeconds: Int = 120,
    val rttLatencyMs: Long = -1L,
    val fps: Float = 0f,
    val bitrateKbps: Long = 0L,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val torchEnabled: Boolean = false,
    val currentZoom: Float = 1.0f,
    val errorMessage: String? = null
)

/**
 * ViewModel del Host de Windows.
 * Gestiona el ciclo de vida del servidor, regeneración de token QR con temporizador TTL (120 s),
 * despacho de comandos de cámara tipados y telemetría (RF-002, RF-004).
 */
class DesktopHostViewModel(
    private val gateway: IStreamGateway,
    private val obsConnector: IObsConnector,
    private val hostIp: String = DesktopNetworkDetector.getLocalIpAddress(),
    private val effectivePort: Int = 8080,
    private val generatePairingPayloadUseCase: GeneratePairingPayloadUseCase = GeneratePairingPayloadUseCase(),
    private val validateSessionTokenUseCase: ValidateSessionTokenUseCase = ValidateSessionTokenUseCase(),
    private val cameraCommandDispatcher: CameraCommandDispatcher = CameraCommandDispatcher(
        capabilities = CameraCapabilities(
            hasTorch = true,
            minZoom = 1.0f,
            maxZoom = 10.0f
        )
    ),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val tickerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : AutoCloseable {

    private val _uiState = MutableStateFlow(
        DesktopHostUiState(
            hostIp = hostIp,
            port = effectivePort
        )
    )
    val uiState: StateFlow<DesktopHostUiState> = _uiState.asStateFlow()

    private var currentMetadata: SessionTokenMetadata? = null
    private var tickerJob: Job? = null

    init {
        generateNewToken()
        startTicker()
        observeIncomingMessages()
        observeObsState()
    }

    private fun generateNewToken() {
        val newToken = UUID.randomUUID().toString().replace("-", "").take(8)
        currentMetadata = SessionTokenMetadata(
            token = newToken,
            createdAtEpochMs = clock(),
            ttlSeconds = 120L
        )
        val payload = generatePairingPayloadUseCase(
            host = hostIp,
            port = effectivePort,
            token = newToken
        )
        _uiState.update {
            it.copy(
                sessionToken = newToken,
                pairingPayload = payload,
                tokenRemainingSeconds = 120
            )
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = coroutineScope.launch(tickerDispatcher) {
            while (isActive) {
                delay(1000L)
                if (_uiState.value.connectionState is ConnectionState.Disconnected) {
                    val remaining = _uiState.value.tokenRemainingSeconds
                    if (remaining <= 1) {
                        generateNewToken()
                    } else {
                        _uiState.update { it.copy(tokenRemainingSeconds = remaining - 1) }
                    }
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        coroutineScope.launch {
            gateway.incomingMessages.collect { msg ->
                handleIncomingMessage(msg)
            }
        }
    }

    private suspend fun handleIncomingMessage(msg: ProtocolMessage) {
        when (msg) {
            is ProtocolMessage.HandshakeInit -> {
                val validation = validateSessionTokenUseCase(
                    presentedToken = msg.sessionToken,
                    metadata = currentMetadata,
                    currentEpochMs = clock()
                )

                if (validation.isValid) {
                    currentMetadata = currentMetadata?.copy(isUsed = true)
                    gateway.sendMessage(
                        ProtocolMessage.HandshakeAck(
                            serverVersion = "1.0.0",
                            approvedFps = 60,
                            approvedResolution = "1080p"
                        )
                    )
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.Connected(sessionInfo = msg.deviceModel),
                            errorMessage = null
                        )
                    }
                    if (obsConnector.connectionState.value == ObsConnectionState.CONNECTED) {
                        obsConnector.setupBrowserSource(
                            previewUrl = "http://$hostIp:$effectivePort/stream/preview"
                        )
                    }
                } else {
                    gateway.sendMessage(
                        ProtocolMessage.DisconnectRequest(reason = "INVALID_TOKEN")
                    )
                }
            }

            is ProtocolMessage.TelemetryPacket -> {
                _uiState.update { current ->
                    current.copy(
                        connectionState = ConnectionState.Streaming,
                        fps = msg.metrics.fps,
                        bitrateKbps = msg.metrics.bitrateKbps,
                        batteryLevel = msg.metrics.batteryLevel,
                        isCharging = msg.metrics.isCharging,
                        rttLatencyMs = if (msg.metrics.latencyMs > 0) msg.metrics.latencyMs else current.rttLatencyMs
                    )
                }
            }

            is ProtocolMessage.Pong -> {
                val rtt = clock() - msg.clientTimestamp
                _uiState.update { it.copy(rttLatencyMs = if (rtt >= 0) rtt else 0L) }
            }

            is ProtocolMessage.DisconnectRequest -> {
                _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
                generateNewToken()
            }

            else -> {
                // Ignore other messages
            }
        }
    }

    private fun observeObsState() {
        coroutineScope.launch {
            obsConnector.connectionState.collect { obsState ->
                _uiState.update { it.copy(obsConnectionState = obsState) }
            }
        }
    }

    fun refreshToken() {
        generateNewToken()
    }

    fun connectObs(host: String = "localhost", port: Int = 4455, password: String? = null) {
        coroutineScope.launch {
            obsConnector.connect(host, port, password)
        }
    }

    fun sendCommand(command: CameraCommand) {
        val dispatchResult = cameraCommandDispatcher.dispatch(command, _uiState.value.connectionState)
        if (dispatchResult is CommandDispatchResult.Success) {
            when (command) {
                is CameraCommand.ToggleTorch -> _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
                is CameraCommand.SetZoom -> _uiState.update { it.copy(currentZoom = command.zoomRatio) }
                else -> {}
            }
            coroutineScope.launch {
                gateway.sendMessage(ProtocolMessage.CommandPacket(command))
            }
        }
    }

    fun onClose() {
        coroutineScope.launch {
            if (_uiState.value.connectionState is ConnectionState.Connected || _uiState.value.connectionState is ConnectionState.Streaming) {
                gateway.sendMessage(ProtocolMessage.DisconnectRequest("HOST_SHUTDOWN"))
            }
            obsConnector.disconnect()
            gateway.disconnect()
            close()
        }
    }

    override fun close() {
        tickerJob?.cancel()
        coroutineScope.coroutineContext[Job]?.cancelChildren()
    }
}
