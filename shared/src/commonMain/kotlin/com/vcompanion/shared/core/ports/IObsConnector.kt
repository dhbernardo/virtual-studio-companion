package com.vcompanion.shared.core.ports

import kotlinx.coroutines.flow.StateFlow

/**
 * Estado de la conexión con OBS Studio WebSocket v5.
 */
enum class ObsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Contrato de puerto para la integración con OBS Studio WebSocket v5.
 * Cumple con Clean Architecture y RNF-003.
 */
interface IObsConnector {
    val connectionState: StateFlow<ObsConnectionState>
    suspend fun connect(host: String = "localhost", port: Int = 4455, password: String? = null): Result<Unit>
    suspend fun disconnect()
    suspend fun setupBrowserSource(
        sourceName: String = "Virtual Studio Camera",
        previewUrl: String,
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 60
    ): Result<Unit>
}
