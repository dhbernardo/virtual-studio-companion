package com.vcompanion.shared.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vcompanion.shared.designsystem.theme.StudioTheme
import com.vcompanion.shared.designsystem.theme.studioGlassmorphic
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.action_settings
import com.vcompanion.shared.resources.action_switch_camera
import com.vcompanion.shared.resources.action_toggle_torch
import com.vcompanion.shared.resources.action_zoom_in
import com.vcompanion.shared.resources.action_zoom_out
import com.vcompanion.shared.resources.ic_flash_off
import com.vcompanion.shared.resources.ic_flash_on
import com.vcompanion.shared.resources.ic_settings
import com.vcompanion.shared.resources.ic_switch_camera
import com.vcompanion.shared.resources.ic_zoom_in
import com.vcompanion.shared.resources.ic_zoom_out
import org.jetbrains.compose.resources.stringResource

/**
 * Barra flotante de controles de cámara con estilo glassmorphic y Touch Targets de 48dp.
 * Cumple con RF-004, RF-006 y RNF-001.
 */
@Composable
fun CameraControlBar(
    onSwitchCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    isTorchOn: Boolean = false,
    isTorchEnabled: Boolean = true,
    isZoomInEnabled: Boolean = true,
    isZoomOutEnabled: Boolean = true,
    onSettingsClick: (() -> Unit)? = null,
    enableBlur: Boolean = true
) {
    val spacing = StudioTheme.spacing
    val colors = StudioTheme.colors

    val barShape = RoundedCornerShape(spacing.section)

    Row(
        modifier = modifier
            .studioGlassmorphic(
                shape = barShape,
                backgroundColor = colors.surface,
                borderColor = colors.borderSubtle,
                enableBlur = enableBlur
            )
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Conmutar cámara (Frontal / Trasera)
        StudioIconButton(
            icon = Res.drawable.ic_switch_camera,
            contentDescription = stringResource(Res.string.action_switch_camera),
            onClick = onSwitchCamera
        )

        // Linterna
        val torchIcon = if (isTorchOn) Res.drawable.ic_flash_on else Res.drawable.ic_flash_off
        val torchTint = if (isTorchOn) colors.warningAccent else colors.textPrimary
        StudioIconButton(
            icon = torchIcon,
            contentDescription = stringResource(Res.string.action_toggle_torch),
            onClick = onToggleTorch,
            enabled = isTorchEnabled,
            tint = torchTint
        )

        // Alejar Zoom
        StudioIconButton(
            icon = Res.drawable.ic_zoom_out,
            contentDescription = stringResource(Res.string.action_zoom_out),
            onClick = onZoomOut,
            enabled = isZoomOutEnabled
        )

        // Acercar Zoom
        StudioIconButton(
            icon = Res.drawable.ic_zoom_in,
            contentDescription = stringResource(Res.string.action_zoom_in),
            onClick = onZoomIn,
            enabled = isZoomInEnabled
        )

        // Ajustes (opcional)
        if (onSettingsClick != null) {
            StudioIconButton(
                icon = Res.drawable.ic_settings,
                contentDescription = stringResource(Res.string.action_settings),
                onClick = onSettingsClick
            )
        }
    }
}
