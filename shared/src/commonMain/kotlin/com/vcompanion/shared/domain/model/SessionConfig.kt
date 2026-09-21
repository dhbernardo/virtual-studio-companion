package com.vcompanion.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * Entidad de dominio pura para configuración de sesión.
 * Cumple con RNF-001 (sin dependencias de plataforma).
 */
@Serializable
data class SessionConfig(
    val sessionId: String,
    val hostIp: String,
    val port: Int,
    val secretToken: String
)
