package com.vcompanion.shared.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.vcompanion.shared.designsystem.theme.StudioTheme

/**
 * Indicador circular luminoso con animación de respiración sutil.
 * Cumple con RF-007 y Constitución Principio 5.
 */
@Composable
fun StudioIndicator(
    color: Color = StudioTheme.colors.standbyAccent,
    isPulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val spacing = StudioTheme.spacing

    val infiniteTransition = rememberInfiniteTransition(label = "StudioIndicatorPulseTransition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StudioIndicatorScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StudioIndicatorAlpha"
    )

    Box(
        modifier = modifier
            .size(spacing.medium)
            .graphicsLayer {
                if (isPulsing) {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
            }
            .background(color = color, shape = CircleShape)
    )
}
