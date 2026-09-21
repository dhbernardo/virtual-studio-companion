package com.vcompanion.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vcompanion.shared.designsystem.theme.StudioTheme

/**
 * Tarjeta de métricas para dashboards con valor numérico destacado y acento temático.
 * Cumple con RF-007, RF-006 y Constitución Principio 5.
 */
@Composable
fun StudioCounterCard(
    label: String,
    value: String,
    unit: String? = null,
    accentColor: Color = StudioTheme.colors.standbyAccent,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    val shape = RoundedCornerShape(spacing.medium + spacing.extraSmall)

    Column(
        modifier = modifier
            .background(color = colors.surfaceElevated, shape = shape)
            .border(width = 1.dp, color = colors.borderSubtle, shape = shape)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.Center
    ) {
        // Etiqueta descriptiva
        Text(
            text = label,
            style = typography.labelSmall,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        // Valor y unidad
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = typography.headlineMedium,
                color = colors.textPrimary
            )

            if (unit != null) {
                Text(
                    text = " $unit",
                    style = typography.titleMedium,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = spacing.extraSmall)
                )
            }
        }
    }
}
