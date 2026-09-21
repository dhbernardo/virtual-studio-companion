package com.vcompanion.shared.core.ports

import com.vcompanion.shared.core.protocol.ProtocolMessage
import kotlinx.coroutines.flow.Flow

/**
 * Puerto de transporte de red para transmisión bidireccional de mensajes de protocolo.
 * Cumple con Clean Architecture y RNF-001.
 */
interface IStreamGateway {
    val incomingMessages: Flow<ProtocolMessage>
    val incomingVideoFrames: Flow<ByteArray> get() = kotlinx.coroutines.flow.emptyFlow()
    suspend fun sendMessage(message: ProtocolMessage): Result<Unit>
    suspend fun disconnect()
}
