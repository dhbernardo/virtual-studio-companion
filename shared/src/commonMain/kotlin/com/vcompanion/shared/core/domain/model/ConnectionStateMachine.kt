package com.vcompanion.shared.core.domain.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class ConnectionStateMachine(
    scope: CoroutineScope? = null,
    reconnectionTimeoutMs: Long = 10_000L
) {
    val state: StateFlow<ConnectionState>
        get() = TODO("Not implemented")

    fun onScanQr(config: PairingConfig) {
        TODO("Not implemented")
    }

    fun onHandshakeSuccess(sessionInfo: String? = null) {
        TODO("Not implemented")
    }

    fun onHandshakeFailed(error: ConnectionError) {
        TODO("Not implemented")
    }

    fun onStartStreaming() {
        TODO("Not implemented")
    }

    fun onStopStreaming() {
        TODO("Not implemented")
    }

    fun onHeartbeatLost() {
        TODO("Not implemented")
    }

    fun onHeartbeatRestored() {
        TODO("Not implemented")
    }

    fun onReconnectionTimeout() {
        TODO("Not implemented")
    }

    fun onDisconnectRequest(reason: String = "USER_REQUEST") {
        TODO("Not implemented")
    }

    fun onError(error: ConnectionError) {
        TODO("Not implemented")
    }

    fun reset() {
        TODO("Not implemented")
    }
}
