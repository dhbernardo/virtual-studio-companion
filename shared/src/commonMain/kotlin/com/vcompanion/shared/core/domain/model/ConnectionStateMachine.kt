package com.vcompanion.shared.core.domain.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Máquina de Estados Finitos (FSM) que orquesta el ciclo de vida de la conexión.
 * Expone un StateFlow inmutable según RF-002, RNF-001 y RNF-002.
 */
class ConnectionStateMachine(
    private val scope: CoroutineScope? = null,
    private val reconnectionTimeoutMs: Long = 10_000L
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var timeoutJob: Job? = null

    fun onScanQr(config: PairingConfig) {
        cancelTimeout()
        _state.value = ConnectionState.Pairing(config)
    }

    fun onHandshakeSuccess(sessionInfo: String? = null) {
        cancelTimeout()
        _state.value = ConnectionState.Connected(sessionInfo)
    }

    fun onHandshakeFailed(error: ConnectionError) {
        cancelTimeout()
        _state.value = ConnectionState.Error(error)
    }

    fun onStartStreaming() {
        if (_state.value is ConnectionState.Connected) {
            cancelTimeout()
            _state.value = ConnectionState.Streaming
        }
    }

    fun onStopStreaming() {
        if (_state.value is ConnectionState.Streaming) {
            cancelTimeout()
            _state.value = ConnectionState.Connected()
        }
    }

    fun onHeartbeatLost() {
        if (_state.value is ConnectionState.Streaming) {
            _state.value = ConnectionState.Reconnecting(attempt = 1)
            startReconnectionTimer()
        }
    }

    fun onHeartbeatRestored() {
        if (_state.value is ConnectionState.Reconnecting) {
            cancelTimeout()
            _state.value = ConnectionState.Streaming
        }
    }

    fun onReconnectionTimeout() {
        if (_state.value is ConnectionState.Reconnecting) {
            cancelTimeout()
            _state.value = ConnectionState.Disconnected
        }
    }

    fun onDisconnectRequest(reason: String = "USER_REQUEST") {
        cancelTimeout()
        _state.value = ConnectionState.Disconnected
    }

    fun onError(error: ConnectionError) {
        cancelTimeout()
        _state.value = ConnectionState.Error(error)
    }

    fun reset() {
        cancelTimeout()
        _state.value = ConnectionState.Disconnected
    }

    private fun startReconnectionTimer() {
        cancelTimeout()
        scope?.let { coroutineScope ->
            timeoutJob = coroutineScope.launch {
                delay(reconnectionTimeoutMs)
                onReconnectionTimeout()
            }
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }
}
