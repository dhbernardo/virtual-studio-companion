package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.PairingConfig

/**
 * Genera la URI estandarizada del Código QR para emparejamiento entre Host y Cliente.
 * Cumple con RF-001 y RNF-001 (sin dependencias de plataforma).
 */
class GeneratePairingPayloadUseCase {

    operator fun invoke(config: PairingConfig): String {
        return buildUri(
            host = config.host,
            port = config.port,
            token = config.sessionToken,
            version = config.version,
            fps = config.recommendedFps,
            res = config.recommendedRes,
            fallbackHosts = config.fallbackHosts
        )
    }

    operator fun invoke(
        host: String,
        port: Int,
        token: String,
        version: Int = 1,
        fps: Int = 60,
        res: String = "1080p",
        fallbackHosts: List<String> = emptyList()
    ): String {
        return buildUri(
            host = host,
            port = port,
            token = token,
            version = version,
            fps = fps,
            res = res,
            fallbackHosts = fallbackHosts
        )
    }

    private fun buildUri(
        host: String,
        port: Int,
        token: String,
        version: Int,
        fps: Int,
        res: String,
        fallbackHosts: List<String>
    ): String {
        val baseUri = "vcam://pair?host=$host&port=$port&token=$token&v=$version&fps=$fps&res=$res"
        return if (fallbackHosts.isNotEmpty()) {
            val fallbackParam = fallbackHosts.joinToString(",")
            "$baseUri&fallbackHosts=$fallbackParam"
        } else {
            baseUri
        }
    }
}
