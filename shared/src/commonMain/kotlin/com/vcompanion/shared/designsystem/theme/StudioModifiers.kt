package com.vcompanion.shared.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modificador de degradación elegante para fondos estilo glassmorphism.
 * Aplica desenfoque en tiempo real cuando está habilitado o translucidez sólida al 85%.
 * Cumple con RF-004 y RNF-001.
 */
@Composable
fun Modifier.studioGlassmorphic(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = StudioTheme.colors.surface,
    borderColor: Color = StudioTheme.colors.borderSubtle,
    blurRadius: Dp = 16.dp,
    enableBlur: Boolean = true
): Modifier {
    val modifierWithBlur = if (enableBlur) {
        this.blur(blurRadius)
    } else {
        this
    }

    return modifierWithBlur
        .background(color = backgroundColor.copy(alpha = 0.85f), shape = shape)
        .border(width = 1.dp, color = borderColor, shape = shape)
}
