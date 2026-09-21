package com.vcompanion.shared.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens de espaciado y dimensiones estandarizadas.
 * Cumple con RF-001 y RF-006 (sin dimensiones en crudo).
 */
@Immutable
data class StudioSpacing(
    val extraSmall: Dp = 2.dp,
    val small: Dp = 4.dp,
    val medium: Dp = 8.dp,
    val large: Dp = 12.dp,
    val extraLarge: Dp = 16.dp,
    val section: Dp = 24.dp,
    val screenEdge: Dp = 32.dp
)

val LocalStudioSpacing = staticCompositionLocalOf { StudioSpacing() }
