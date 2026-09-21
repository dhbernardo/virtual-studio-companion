package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.ConnectionErrorCode
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.core.domain.model.PairingPayloadException

/**
 * Parsea y valida el payload de la URI escaneada de un Código QR.
 * Cumple con RF-001 y RNF-001 (sin dependencias de plataforma).
 */
class ParsePairingPayloadUseCase {

    companion object {
        private const val SCHEME_PREFIX = "vcam://pair?"
    }

    operator fun invoke(rawUri: String): PairingConfig {
        if (!rawUri.startsWith(SCHEME_PREFIX)) {
            throw PairingPayloadException(
                errorCode = ConnectionErrorCode.INVALID_URI_SCHEME,
                message = "Invalid URI scheme or path. Expected prefix '$SCHEME_PREFIX'"
            )
        }

        val queryString = rawUri.substring(SCHEME_PREFIX.length)
        val queryParams = parseQueryParams(queryString)

        val host = queryParams["host"]?.takeIf { it.isNotBlank() }
            ?: throw PairingPayloadException(
                errorCode = ConnectionErrorCode.MALFORMED_URI,
                message = "Missing required 'host' parameter"
            )

        val portStr = queryParams["port"]?.takeIf { it.isNotBlank() }
            ?: throw PairingPayloadException(
                errorCode = ConnectionErrorCode.MALFORMED_URI,
                message = "Missing required 'port' parameter"
            )

        val port = portStr.toIntOrNull()
            ?: throw PairingPayloadException(
                errorCode = ConnectionErrorCode.MALFORMED_URI,
                message = "Invalid 'port' format"
            )

        val token = queryParams["token"]?.takeIf { it.isNotBlank() }
            ?: throw PairingPayloadException(
                errorCode = ConnectionErrorCode.MALFORMED_URI,
                message = "Missing required 'token' parameter"
            )

        val version = queryParams["v"]?.toIntOrNull() ?: 1
        val fps = queryParams["fps"]?.toIntOrNull() ?: 60
        val res = queryParams["res"]?.takeIf { it.isNotBlank() } ?: "1080p"

        val fallbackHosts = queryParams["fallbackHosts"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        return PairingConfig(
            host = host,
            port = port,
            sessionToken = token,
            version = version,
            recommendedFps = fps,
            recommendedRes = res,
            fallbackHosts = fallbackHosts
        )
    }

    fun parseOrNull(rawUri: String): PairingConfig? {
        return try {
            invoke(rawUri)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()

        val map = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.isNotEmpty()) {
                val key = parts[0].trim()
                val value = if (parts.size > 1) parts[1].trim() else ""
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }
}
