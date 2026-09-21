package com.vcompanion.shared.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.designsystem.theme.StudioTheme
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.status_alert
import com.vcompanion.shared.resources.status_live
import com.vcompanion.shared.resources.status_offline
import com.vcompanion.shared.resources.status_rec
import com.vcompanion.shared.resources.status_standby
import org.jetbrains.compose.resources.stringResource

/**
 * Componente visual de grado broadcast que indica el estado del stream.
 * Utiliza graphicsLayer para animaciones continuas a 60 FPS sin recomposición.
 * Cumple con RF-002, RF-006 y RNF-002.
 */
@Composable
fun StudioBadge(
    status: StreamStatus,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    val isPulsing = status == StreamStatus.LIVE || status == StreamStatus.REC || status == StreamStatus.ALERT

    val infiniteTransition = rememberInfiniteTransition(label = "StudioBadgePulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StudioBadgePulseAlpha"
    )

    val (accentColor, backgroundColor, borderColor, labelRes) = when (status) {
        StreamStatus.LIVE -> Tuple4(
            colors.liveAccent,
            colors.liveAccent.copy(alpha = 0.15f),
            colors.liveAccent.copy(alpha = 0.4f),
            Res.string.status_live
        )
        StreamStatus.REC -> Tuple4(
            colors.liveAccent,
            colors.liveAccent.copy(alpha = 0.15f),
            colors.liveAccent.copy(alpha = 0.4f),
            Res.string.status_rec
        )
        StreamStatus.ALERT -> Tuple4(
            colors.warningAccent,
            colors.warningAccent.copy(alpha = 0.15f),
            colors.warningAccent.copy(alpha = 0.4f),
            Res.string.status_alert
        )
        StreamStatus.STANDBY -> Tuple4(
            colors.standbyAccent,
            colors.standbyAccent.copy(alpha = 0.15f),
            colors.standbyAccent.copy(alpha = 0.4f),
            Res.string.status_standby
        )
        StreamStatus.OFFLINE -> Tuple4(
            colors.textSecondary,
            colors.surfaceElevated,
            colors.borderSubtle,
            Res.string.status_offline
        )
    }

    val shape = RoundedCornerShape(spacing.small + spacing.extraSmall)

    Row(
        modifier = modifier
            .background(color = backgroundColor, shape = shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(spacing.medium)
                .graphicsLayer {
                    alpha = if (isPulsing) pulseAlpha else 1.0f
                }
                .background(color = accentColor, shape = CircleShape)
        )

        Text(
            text = stringResource(labelRes),
            style = typography.labelSmall,
            color = accentColor,
            modifier = Modifier.padding(start = spacing.small)
        )
    }
}

@Composable
fun StudioBadge(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    StudioBadge(
        status = connectionState.toStreamStatus(),
        modifier = modifier
    )
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
