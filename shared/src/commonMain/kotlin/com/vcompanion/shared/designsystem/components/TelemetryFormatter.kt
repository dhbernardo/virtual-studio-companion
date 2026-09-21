package com.vcompanion.shared.designsystem.components

import androidx.compose.ui.graphics.Color
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.designsystem.theme.StudioColors

/**
 * Formateador de métricas y evaluación de colores semánticos para TelemetryPill.
 * Cumple con RF-003.
 */
object TelemetryFormatter {

    const val PLACEHOLDER = "--- ms / -- FPS"

    fun format(metrics: DeviceMetrics?): String {
        if (metrics == null) return PLACEHOLDER
        val fpsInt = metrics.fps.toInt()
        return "${metrics.latencyMs} ms / $fpsInt FPS"
    }

    fun getLatencyColor(latencyMs: Long?, colors: StudioColors): Color {
        if (latencyMs == null) return colors.textSecondary
        return when {
            latencyMs <= 50L -> colors.standbyAccent
            latencyMs <= 120L -> colors.warningAccent
            else -> colors.liveAccent
        }
    }
}
