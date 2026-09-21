package com.vcompanion.shared.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Representa un error tipado de conexión para el dominio y FSM.
 * Cumple con RF-002 y RNF-001 (sin textos en crudo ni dependencias de plataforma).
 */
@Serializable
data class ConnectionError(
    val code: ConnectionErrorCode,
    val messageKey: String? = null
)
