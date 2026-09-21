package com.vcompanion.shared.core.ports

import com.vcompanion.shared.core.domain.model.PairingConfig

/**
 * Puerto para codificación y decodificación de payloads de Código QR.
 * Cumple con Clean Architecture y RNF-001.
 */
interface IQrCodec {
    fun encode(config: PairingConfig): String
    fun decode(rawUri: String): PairingConfig
}
