package com.vcompanion.shared.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LensFacing {
    BACK,
    FRONT
}

@Serializable
sealed class CameraCommand {
    @Serializable
    @SerialName("SET_ZOOM")
    data class SetZoom(val zoomRatio: Float) : CameraCommand()

    @Serializable
    @SerialName("TOGGLE_TORCH")
    data object ToggleTorch : CameraCommand()

    @Serializable
    @SerialName("SWITCH_LENS")
    data class SwitchLens(val facing: String) : CameraCommand()
}
