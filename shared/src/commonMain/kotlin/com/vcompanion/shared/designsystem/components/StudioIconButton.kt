package com.vcompanion.shared.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vcompanion.shared.designsystem.theme.StudioTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Botón circular para controles de cámara con Touch Target mínimo de 48dp y estado deshabilitado.
 * Cumple con RF-004 y RF-006.
 */
@Composable
fun StudioIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = StudioTheme.colors.textPrimary
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp) // Touch Target mínimo de 48dp según RF-004
            .alpha(if (enabled) 1.0f else 0.38f) // 38% de opacidad cuando está deshabilitado
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
