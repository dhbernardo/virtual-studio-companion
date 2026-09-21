package com.vcompanion.desktop.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vcompanion.desktop.presentation.viewmodel.DesktopHostUiState
import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.ports.ObsConnectionState
import com.vcompanion.shared.designsystem.components.StudioBadge
import com.vcompanion.shared.designsystem.components.StudioCounterCard
import com.vcompanion.shared.designsystem.components.StudioIndicator
import com.vcompanion.shared.designsystem.theme.StudioTheme
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.action_disconnect
import com.vcompanion.shared.resources.desktop_action_connect_obs
import com.vcompanion.shared.resources.desktop_action_disconnect_obs
import com.vcompanion.shared.resources.action_switch_camera
import com.vcompanion.shared.resources.action_toggle_torch
import com.vcompanion.shared.resources.action_zoom_in
import com.vcompanion.shared.resources.action_zoom_out
import com.vcompanion.shared.resources.desktop_battery_label
import com.vcompanion.shared.resources.desktop_camera_controls_title
import com.vcompanion.shared.resources.desktop_charging_status
import com.vcompanion.shared.resources.desktop_default_device
import com.vcompanion.shared.resources.desktop_obs_connected
import com.vcompanion.shared.resources.desktop_obs_connecting
import com.vcompanion.shared.resources.desktop_obs_disconnected
import com.vcompanion.shared.resources.desktop_obs_error
import com.vcompanion.shared.resources.desktop_session_info_label
import com.vcompanion.shared.resources.desktop_zoom_level
import com.vcompanion.shared.resources.label_bitrate
import com.vcompanion.shared.resources.label_fps
import com.vcompanion.shared.resources.label_latency
import com.vcompanion.shared.resources.telemetry_bitrate_unit
import com.vcompanion.shared.resources.telemetry_fps_unit
import com.vcompanion.shared.resources.telemetry_latency_unit
import com.vcompanion.shared.resources.telemetry_placeholder
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.vcompanion.shared.resources.desktop_return_monitor_title
import com.vcompanion.shared.resources.desktop_waiting_video_signal

/**
 * Dashboard de telemetría, monitor de retorno y controles remotos de cámara para Windows Host.
 * Consume [StudioCounterCard], [StudioBadge], [StudioIndicator] y renderizado Skia nativo (RF-004, RNF-004).
 */
@Composable
fun StreamingDashboardView(
    state: DesktopHostUiState,
    onSendCommand: (CameraCommand) -> Unit,
    onDisconnect: () -> Unit,
    onConnectObs: () -> Unit = {},
    onDisconnectObs: () -> Unit = {},
    videoFrame: ImageBitmap? = null,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(spacing.large)
    ) {
        // Cabecera: Badge de estado de streaming, estado OBS y botón desconectar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudioBadge(connectionState = state.connectionState)

                Spacer(modifier = Modifier.width(spacing.large))

                // Estado de OBS Studio
                val obsColor = when (state.obsConnectionState) {
                    ObsConnectionState.CONNECTED -> colors.standbyAccent
                    ObsConnectionState.CONNECTING -> colors.warningAccent
                    ObsConnectionState.ERROR -> colors.liveAccent
                    ObsConnectionState.DISCONNECTED -> colors.textSecondary
                }
                val obsText = when (state.obsConnectionState) {
                    ObsConnectionState.CONNECTED -> stringResource(Res.string.desktop_obs_connected)
                    ObsConnectionState.CONNECTING -> stringResource(Res.string.desktop_obs_connecting)
                    ObsConnectionState.ERROR -> stringResource(Res.string.desktop_obs_error)
                    ObsConnectionState.DISCONNECTED -> stringResource(Res.string.desktop_obs_disconnected)
                }

                StudioIndicator(
                    color = obsColor,
                    isPulsing = state.obsConnectionState == ObsConnectionState.CONNECTING
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text(
                    text = obsText,
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.width(spacing.medium))
                when (state.obsConnectionState) {
                    ObsConnectionState.CONNECTED -> {
                        Button(
                            onClick = onDisconnectObs,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.surfaceElevated,
                                contentColor = colors.liveAccent
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.desktop_action_disconnect_obs),
                                style = typography.labelSmall
                            )
                        }
                    }
                    ObsConnectionState.CONNECTING -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.surfaceElevated,
                                contentColor = colors.textSecondary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.desktop_obs_connecting),
                                style = typography.labelSmall
                            )
                        }
                    }
                    ObsConnectionState.DISCONNECTED,
                    ObsConnectionState.ERROR -> {
                        Button(
                            onClick = onConnectObs,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.surfaceElevated,
                                contentColor = colors.textPrimary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.desktop_action_connect_obs),
                                style = typography.labelSmall
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceElevated,
                    contentColor = colors.liveAccent
                )
            ) {
                Text(
                    text = stringResource(Res.string.action_disconnect),
                    style = typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        // Info del dispositivo móvil y batería
        val defaultDevice = stringResource(Res.string.desktop_default_device)
        val deviceModel = (state.connectionState as? ConnectionState.Connected)?.sessionInfo ?: defaultDevice
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.desktop_session_info_label),
                    style = typography.bodyMedium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text(
                    text = deviceModel,
                    style = typography.titleMedium,
                    color = colors.textPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.desktop_battery_label),
                    style = typography.bodyMedium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.width(spacing.small))
                val batteryText = if (state.isCharging) {
                    "${state.batteryLevel}% (${stringResource(Res.string.desktop_charging_status)})"
                } else {
                    "${state.batteryLevel}%"
                }
                Text(
                    text = batteryText,
                    style = typography.titleMedium,
                    color = if (state.batteryLevel < 20) colors.warningAccent else colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.large))

        // Tarjetas de telemetría (FPS, Bitrate, Latencia RTT)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            StudioCounterCard(
                label = stringResource(Res.string.label_fps),
                value = state.fps.toInt().toString(),
                unit = stringResource(Res.string.telemetry_fps_unit),
                accentColor = colors.standbyAccent,
                modifier = Modifier.weight(1f)
            )

            StudioCounterCard(
                label = stringResource(Res.string.label_bitrate),
                value = state.bitrateKbps.toString(),
                unit = stringResource(Res.string.telemetry_bitrate_unit),
                accentColor = colors.standbyAccent,
                modifier = Modifier.weight(1f)
            )

            val placeholder = stringResource(Res.string.telemetry_placeholder)
            val latencyValue = if (state.rttLatencyMs >= 0) state.rttLatencyMs.toString() else placeholder.take(3)
            val latencyColor = if (state.rttLatencyMs in 0..60) colors.standbyAccent else colors.warningAccent

            StudioCounterCard(
                label = stringResource(Res.string.label_latency),
                value = latencyValue,
                unit = stringResource(Res.string.telemetry_latency_unit),
                accentColor = latencyColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        // Monitor de Retorno Nativo (Skia)
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(spacing.medium))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(spacing.medium)),
            contentAlignment = Alignment.Center
        ) {
            if (videoFrame != null) {
                Image(
                    bitmap = videoFrame,
                    contentDescription = stringResource(Res.string.desktop_return_monitor_title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    StudioIndicator(color = colors.standbyAccent, isPulsing = true)
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = stringResource(Res.string.desktop_waiting_video_signal),
                        style = typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        // Controles de cámara remota
        Text(
            text = stringResource(Res.string.desktop_camera_controls_title),
            style = typography.titleMedium,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(spacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            // Linterna
            Button(
                onClick = { onSendCommand(CameraCommand.ToggleTorch) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.torchEnabled) colors.standbyAccent else colors.surfaceElevated,
                    contentColor = if (state.torchEnabled) colors.background else colors.textPrimary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(Res.string.action_toggle_torch),
                    style = typography.labelSmall
                )
            }

            // Cambiar cámara
            Button(
                onClick = { onSendCommand(CameraCommand.SwitchLens("FRONT")) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceElevated,
                    contentColor = colors.textPrimary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(Res.string.action_switch_camera),
                    style = typography.labelSmall
                )
            }

            // Zoom In
            Button(
                onClick = { onSendCommand(CameraCommand.SetZoom((state.currentZoom + 0.5f).coerceAtMost(10f))) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceElevated,
                    contentColor = colors.textPrimary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(Res.string.action_zoom_in),
                    style = typography.labelSmall
                )
            }

            // Zoom Out
            Button(
                onClick = { onSendCommand(CameraCommand.SetZoom((state.currentZoom - 0.5f).coerceAtLeast(1f))) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceElevated,
                    contentColor = colors.textPrimary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(Res.string.action_zoom_out),
                    style = typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            text = "${stringResource(Res.string.desktop_zoom_level)}: ${state.currentZoom}x",
            style = typography.labelSmall,
            color = colors.textSecondary
        )
    }
}
