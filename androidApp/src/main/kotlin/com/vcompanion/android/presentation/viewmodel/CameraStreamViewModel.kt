package com.vcompanion.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.CommandDispatchResult
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.domain.model.LensFacing
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.core.domain.model.ThermalState
import com.vcompanion.shared.core.domain.usecase.CameraCommandDispatcher
import com.vcompanion.shared.core.ports.IStreamGateway
import com.vcompanion.shared.core.ports.ITelemetryEmitter
import com.vcompanion.shared.core.protocol.ProtocolMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CameraUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val deviceMetrics: DeviceMetrics = DeviceMetrics(
        fps = 60f,
        bitrateKbps = 0L,
        latencyMs = 0L,
        batteryLevel = 100,
        isCharging = false,
        thermalState = ThermalState.NOMINAL
    ),
    val currentFps: Int = 60,
    val currentZoomRatio: Float = 1.0f,
    val isTorchEnabled: Boolean = false,
    val currentLens: LensFacing = LensFacing.BACK,
    val isThermalAlertActive: Boolean = false,
    val hasFlashUnit: Boolean = true
)

class CameraStreamViewModel(
    private val streamGateway: IStreamGateway,
    private val telemetryEmitter: ITelemetryEmitter,
    private val commandDispatcher: CameraCommandDispatcher = CameraCommandDispatcher(),
    val hasFlashUnit: Boolean = true,
    val initialConfig: PairingConfig? = null,
    private val onApplyZoom: ((Float) -> Boolean)? = null,
    private val onToggleTorch: ((Boolean) -> Boolean)? = null,
    private val onSetFps: ((Int) -> Unit)? = null,
    private val onGetLiveFps: (() -> Float)? = null,
    private val onGetLiveBitrate: (() -> Long)? = null,
    private val periodicDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CameraUiState(
            connectionState = if (initialConfig != null) ConnectionState.Pairing(initialConfig) else ConnectionState.Disconnected,
            hasFlashUnit = hasFlashUnit
        )
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val sessionJobs = mutableListOf<Job>()

    fun startSession(config: PairingConfig) {
        _uiState.update { it.copy(connectionState = ConnectionState.Pairing(config)) }

        val messagesJob = viewModelScope.launch {
            streamGateway.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
        sessionJobs.add(messagesJob)

        val telemetryJob = viewModelScope.launch {
            telemetryEmitter.telemetryFlow.collect { metrics ->
                handleTelemetry(metrics)
            }
        }
        sessionJobs.add(telemetryJob)

        val periodicTelemetryJob = viewModelScope.launch(periodicDispatcher) {
            while (isActive) {
                delay(1000L)
                if (_uiState.value.connectionState is ConnectionState.Streaming || _uiState.value.connectionState is ConnectionState.Connected) {
                    val baseMetrics = telemetryEmitter.captureCurrentMetrics()
                    val liveFps = onGetLiveFps?.invoke() ?: _uiState.value.currentFps.toFloat()
                    val liveBitrate = onGetLiveBitrate?.invoke() ?: baseMetrics.bitrateKbps
                    val currentMetrics = baseMetrics.copy(
                        fps = liveFps,
                        bitrateKbps = liveBitrate
                    )
                    val packet = ProtocolMessage.TelemetryPacket(
                        timestamp = System.currentTimeMillis(),
                        metrics = currentMetrics
                    )
                    try {
                        streamGateway.sendMessage(packet)
                    } catch (_: Exception) {}
                }
            }
        }
        sessionJobs.add(periodicTelemetryJob)

        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Streaming,
                currentFps = config.recommendedFps
            )
        }
    }

    private fun handleIncomingMessage(message: ProtocolMessage) {
        when (message) {
            is ProtocolMessage.CommandPacket -> {
                val result = commandDispatcher.dispatch(message.command, _uiState.value.connectionState)
                if (result is CommandDispatchResult.Success) {
                    when (val cmd = result.command) {
                        is CameraCommand.SetZoom -> {
                            val applied = onApplyZoom?.invoke(cmd.zoomRatio) ?: true
                            if (applied) {
                                _uiState.update { it.copy(currentZoomRatio = cmd.zoomRatio) }
                            }
                        }
                        is CameraCommand.ToggleTorch -> {
                            if (hasFlashUnit) {
                                val nextTorch = !_uiState.value.isTorchEnabled
                                val applied = onToggleTorch?.invoke(nextTorch) ?: true
                                if (applied) {
                                    _uiState.update { it.copy(isTorchEnabled = nextTorch) }
                                }
                            }
                        }
                        is CameraCommand.SwitchLens -> {
                            val facing = try {
                                LensFacing.valueOf(cmd.facing.uppercase())
                            } catch (_: Exception) {
                                null
                            }
                            if (facing != null) {
                                _uiState.update { it.copy(currentLens = facing) }
                            }
                        }
                    }
                }
            }
            is ProtocolMessage.DisconnectRequest -> {
                disconnect()
            }
            else -> {
                // Ignore other messages
            }
        }
    }

    private fun handleTelemetry(metrics: DeviceMetrics) {
        val isSevere = metrics.thermalState == ThermalState.SERIOUS || metrics.thermalState == ThermalState.CRITICAL
        val nextFps = if (isSevere) 30 else _uiState.value.currentFps

        if (isSevere && _uiState.value.currentFps > 30) {
            onSetFps?.invoke(30)
        }

        val liveFps = onGetLiveFps?.invoke() ?: metrics.fps
        val liveBitrate = onGetLiveBitrate?.invoke() ?: metrics.bitrateKbps
        val updatedMetrics = metrics.copy(fps = liveFps, bitrateKbps = liveBitrate)

        _uiState.update { current ->
            current.copy(
                deviceMetrics = updatedMetrics,
                currentFps = nextFps,
                isThermalAlertActive = isSevere
            )
        }
    }

    fun setZoom(ratio: Float) {
        val applied = onApplyZoom?.invoke(ratio) ?: true
        if (applied) {
            _uiState.update { it.copy(currentZoomRatio = ratio) }
            viewModelScope.launch {
                try {
                    streamGateway.sendMessage(ProtocolMessage.CommandPacket(CameraCommand.SetZoom(ratio)))
                } catch (_: Exception) {}
            }
        }
    }

    fun toggleTorch() {
        if (!hasFlashUnit) return
        val nextState = !_uiState.value.isTorchEnabled
        val applied = onToggleTorch?.invoke(nextState) ?: true
        if (applied) {
            _uiState.update { it.copy(isTorchEnabled = nextState) }
            viewModelScope.launch {
                try {
                    streamGateway.sendMessage(ProtocolMessage.CommandPacket(CameraCommand.ToggleTorch))
                } catch (_: Exception) {}
            }
        }
    }

    fun disconnect(reason: String = "USER_REQUEST") {
        viewModelScope.launch {
            try {
                streamGateway.sendMessage(ProtocolMessage.DisconnectRequest(reason))
            } catch (_: Exception) {}
            try {
                streamGateway.disconnect()
            } catch (_: Exception) {}
            sessionJobs.forEach { it.cancel() }
            sessionJobs.clear()
            _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
