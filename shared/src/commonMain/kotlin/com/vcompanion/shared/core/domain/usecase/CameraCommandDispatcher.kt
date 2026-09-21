package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.CameraCapabilities
import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.CommandDispatchResult
import com.vcompanion.shared.core.domain.model.CommandErrorCode
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.domain.model.LensFacing

/**
 * Valida y despacha comandos de control de cámara según el estado de la FSM y capacidades de hardware.
 * Cumple con RF-004 y RNF-001 (sin dependencias de plataforma ni cadenas en crudo).
 */
class CameraCommandDispatcher(
    val capabilities: CameraCapabilities = CameraCapabilities()
) {

    fun dispatch(
        command: CameraCommand,
        connectionState: ConnectionState
    ): CommandDispatchResult {
        if (connectionState !is ConnectionState.Connected && connectionState !is ConnectionState.Streaming) {
            return CommandDispatchResult.Rejected(CommandErrorCode.INVALID_STATE)
        }

        return when (command) {
            is CameraCommand.SetZoom -> {
                if (command.zoomRatio in capabilities.minZoom..capabilities.maxZoom) {
                    CommandDispatchResult.Success(command)
                } else {
                    CommandDispatchResult.Rejected(CommandErrorCode.VALUE_OUT_OF_RANGE)
                }
            }
            is CameraCommand.ToggleTorch -> {
                if (capabilities.hasTorch) {
                    CommandDispatchResult.Success(command)
                } else {
                    CommandDispatchResult.Rejected(CommandErrorCode.UNSUPPORTED_HARDWARE)
                }
            }
            is CameraCommand.SwitchLens -> {
                val parsedFacing = try {
                    LensFacing.valueOf(command.facing.uppercase())
                } catch (_: Exception) {
                    null
                }

                if (parsedFacing != null && capabilities.supportedLensFacing.contains(parsedFacing)) {
                    CommandDispatchResult.Success(command)
                } else {
                    CommandDispatchResult.Rejected(CommandErrorCode.UNSUPPORTED_HARDWARE)
                }
            }
        }
    }
}
