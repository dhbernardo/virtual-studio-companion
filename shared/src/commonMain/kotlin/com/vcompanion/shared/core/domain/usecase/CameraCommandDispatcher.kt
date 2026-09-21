package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.CameraCapabilities
import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.CommandDispatchResult
import com.vcompanion.shared.core.domain.model.ConnectionState

class CameraCommandDispatcher(
    val capabilities: CameraCapabilities = CameraCapabilities()
) {

    fun dispatch(
        command: CameraCommand,
        connectionState: ConnectionState
    ): CommandDispatchResult {
        TODO("Not implemented")
    }
}
