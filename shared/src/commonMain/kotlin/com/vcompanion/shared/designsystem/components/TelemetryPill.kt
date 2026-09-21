package com.vcompanion.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.designsystem.theme.StudioTheme
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.telemetry_fps_unit
import com.vcompanion.shared.resources.telemetry_latency_unit
import com.vcompanion.shared.resources.telemetry_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * Píldora de telemetría de transmisión que muestra latencia y FPS con colores semánticos reactivos.
 * Cumple con RF-003, RF-006 y RNF-002.
 */
@Composable
fun TelemetryPill(
    metrics: DeviceMetrics?,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    val shape = RoundedCornerShape(spacing.large)

    Row(
        modifier = modifier
            .background(color = colors.surfaceElevated, shape = shape)
            .border(width = 1.dp, color = colors.borderSubtle, shape = shape)
            .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (metrics == null) {
            Text(
                text = stringResource(Res.string.telemetry_placeholder),
                style = typography.monoNumeric,
                color = colors.textSecondary
            )
        } else {
            val latencyColor = TelemetryFormatter.getLatencyColor(metrics.latencyMs, colors)
            val latencyUnit = stringResource(Res.string.telemetry_latency_unit)
            val fpsUnit = stringResource(Res.string.telemetry_fps_unit)

            // Indicador de latencia
            Box(
                modifier = Modifier
                    .size(spacing.small + spacing.extraSmall)
                    .background(color = latencyColor, shape = CircleShape)
            )

            Spacer(modifier = Modifier.width(spacing.small))

            // Valor de latencia
            Text(
                text = "${metrics.latencyMs} $latencyUnit",
                style = typography.monoNumeric,
                color = latencyColor
            )

            // Separador
            Text(
                text = " • ",
                style = typography.monoNumeric,
                color = colors.textSecondary
            )

            // Valor de FPS
            Text(
                text = "${metrics.fps.toInt()} $fpsUnit",
                style = typography.monoNumeric,
                color = colors.textPrimary
            )
        }
    }
}
