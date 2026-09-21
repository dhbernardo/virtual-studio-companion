package com.vcompanion.shared.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalStudioColors = staticCompositionLocalOf { StudioDarkColors }

/**
 * Acceso centralizado a los tokens del tema activo.
 * Cumple con RF-001 y RF-005.
 */
object StudioTheme {
    val colors: StudioColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioColors.current

    val typography: StudioTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioTypography.current

    val spacing: StudioSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioSpacing.current
}

/**
 * Composable raíz del tema que provee tokens simétricos Dark y Light con soporte para
 * detección del sistema y selección manual.
 */
@Composable
fun StudioTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (preference) {
        ThemePreference.SYSTEM -> systemInDark
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }

    val colors = if (isDark) StudioDarkColors else StudioLightColors
    val materialColorScheme = if (isDark) {
        darkColorScheme(
            background = colors.background,
            surface = colors.surface,
            primary = colors.standbyAccent,
            error = colors.liveAccent,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary
        )
    } else {
        lightColorScheme(
            background = colors.background,
            surface = colors.surface,
            primary = colors.standbyAccent,
            error = colors.liveAccent,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary
        )
    }

    CompositionLocalProvider(
        LocalStudioColors provides colors,
        LocalStudioSpacing provides StudioSpacing(),
        LocalStudioTypography provides StudioTypography()
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
