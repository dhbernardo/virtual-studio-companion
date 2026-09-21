package com.vcompanion.shared.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PairingConfig(
    val host: String,
    val port: Int,
    val sessionToken: String,
    val version: Int = 1,
    val recommendedFps: Int = 60,
    val recommendedRes: String = "1080p",
    val fallbackHosts: List<String> = emptyList()
)

class PairingPayloadException(
    val errorCode: ConnectionErrorCode,
    override val message: String? = null
) : Exception(message)
