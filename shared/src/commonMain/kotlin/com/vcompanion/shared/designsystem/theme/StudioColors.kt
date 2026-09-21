package com.vcompanion.shared.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Contrato inmutable de tokens de color para el sistema de diseño de Virtual Studio Companion.
 * Cumple con RF-001, RNF-001 y RNF-003 (WCAG AA).
 */
@Immutable
data class StudioColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val liveAccent: Color,
    val standbyAccent: Color,
    val warningAccent: Color,
    val borderSubtle: Color
)
