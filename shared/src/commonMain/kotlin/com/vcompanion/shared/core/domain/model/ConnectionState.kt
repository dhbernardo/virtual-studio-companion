package com.vcompanion.shared.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Jerarquía sellada que define los estados finitos del ciclo de vida de la conexión (FSM).
 * Cumple con RF-002 y RNF-001.
 */
@Serializable
sealed interface ConnectionState {

    @Serializable
    data object Disconnected : ConnectionState

    @Serializable
    data object Discovering : ConnectionState

    @Serializable
    data class Pairing(val config: PairingConfig) : ConnectionState

    @Serializable
    data class Connected(val sessionInfo: String? = null) : ConnectionState

    @Serializable
    data object Streaming : ConnectionState

    @Serializable
    data class Reconnecting(val attempt: Int = 1) : ConnectionState

    @Serializable
    data class Error(val reason: ConnectionError) : ConnectionState
}
