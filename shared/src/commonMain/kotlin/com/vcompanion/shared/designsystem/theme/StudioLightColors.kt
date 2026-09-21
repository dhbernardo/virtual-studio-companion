package com.vcompanion.shared.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores para modo claro (Studio Light) con acentos de alto contraste WCAG AA (>4.5:1).
 * Cumple con RF-001 y RNF-003.
 */
val StudioLightColors = StudioColors(
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEAEFF5),
    textPrimary = Color(0xFF24292F),
    textSecondary = Color(0xFF57606A),
    liveAccent = Color(0xFFCF222E),       // WCAG AA > 4.5:1 sobre Surface #FFFFFF
    standbyAccent = Color(0xFF1A7F37),    // WCAG AA > 4.5:1 sobre Surface #FFFFFF
    warningAccent = Color(0xFF9A6700),    // WCAG AA > 4.5:1 sobre Surface #FFFFFF
    borderSubtle = Color(0xFFD0D7DE)
)
