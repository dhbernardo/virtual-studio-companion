package com.vcompanion.shared.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CameraCapabilities(
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 5.0f,
    val hasTorch: Boolean = true,
    val supportedLensFacing: Set<LensFacing> = setOf(LensFacing.BACK, LensFacing.FRONT)
)

sealed interface CommandDispatchResult {
    data class Success(val command: CameraCommand) : CommandDispatchResult
    data class Rejected(val errorCode: CommandErrorCode) : CommandDispatchResult

    val isSuccess: Boolean get() = this is Success
}
