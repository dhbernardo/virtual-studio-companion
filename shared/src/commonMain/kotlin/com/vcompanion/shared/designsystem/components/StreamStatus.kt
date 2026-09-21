package com.vcompanion.shared.designsystem.components

import com.vcompanion.shared.core.domain.model.ConnectionState

/**
 * Estados visuales de transmisión para badges e indicadores de estudio.
 * Cumple con RF-002 y RNF-001.
 */
enum class StreamStatus {
    OFFLINE,
    STANDBY,
    LIVE,
    ALERT,
    REC
}

/**
 * Función canónica de mapeo entre la FSM de conexión de dominio y el estado visual de la suite.
 */
fun ConnectionState.toStreamStatus(): StreamStatus {
    return when (this) {
        is ConnectionState.Disconnected -> StreamStatus.OFFLINE
        is ConnectionState.Discovering -> StreamStatus.STANDBY
        is ConnectionState.Pairing -> StreamStatus.STANDBY
        is ConnectionState.Connected -> StreamStatus.STANDBY
        is ConnectionState.Streaming -> StreamStatus.LIVE
        is ConnectionState.Reconnecting -> StreamStatus.ALERT
        is ConnectionState.Error -> StreamStatus.ALERT
    }
}
