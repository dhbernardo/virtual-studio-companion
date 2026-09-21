package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.PairingConfig

class GeneratePairingPayloadUseCase {
    operator fun invoke(config: PairingConfig): String {
        TODO("Not implemented")
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
        TODO("Not implemented")
    }
}
