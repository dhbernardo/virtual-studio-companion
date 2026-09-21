package com.vcompanion.desktop.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vcompanion.desktop.presentation.viewmodel.DesktopHostUiState
import com.vcompanion.shared.core.ports.ObsConnectionState
import com.vcompanion.shared.designsystem.components.QrCard
import com.vcompanion.shared.designsystem.components.StudioIndicator
import com.vcompanion.shared.designsystem.theme.StudioTheme
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.desktop_action_connect_obs
import com.vcompanion.shared.resources.desktop_action_refresh_qr
import com.vcompanion.shared.resources.desktop_host_address_label
import com.vcompanion.shared.resources.desktop_obs_connected
import com.vcompanion.shared.resources.desktop_obs_connecting
import com.vcompanion.shared.resources.desktop_obs_disconnected
import com.vcompanion.shared.resources.desktop_obs_error
import com.vcompanion.shared.resources.desktop_seconds_suffix
import com.vcompanion.shared.resources.desktop_token_expires_label
import com.vcompanion.shared.resources.qr_description
import com.vcompanion.shared.resources.qr_title
import org.jetbrains.compose.resources.stringResource

/**
 * Vista de emparejamiento con Código QR dinámico, temporizador de token TTL (120 s)
 * e indicador de estado de OBS Studio (RF-002, RF-003, RNF-004).
 */
@Composable
fun QrPairingView(
    state: DesktopHostUiState,
    onRefreshToken: () -> Unit,
    onConnectObs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Barra superior con estado de OBS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            }

            if (state.obsConnectionState != ObsConnectionState.CONNECTED) {
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

        Spacer(modifier = Modifier.height(spacing.medium))

        // Tarjeta con Código QR
        QrCard(
            title = stringResource(Res.string.qr_title),
            description = stringResource(Res.string.qr_description),
            qrContent = {
                QrCodeCanvas(payload = state.pairingPayload)
            }
        )

        Spacer(modifier = Modifier.height(spacing.large))

        // Dirección IP y puerto legible
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.desktop_host_address_label),
                style = typography.bodyMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.width(spacing.small))
            Text(
                text = "${state.hostIp}:${state.port}",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        // Barra de progreso y temporizador TTL (120 s)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(280.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.desktop_token_expires_label),
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
                Text(
                    text = "${state.tokenRemainingSeconds} ${stringResource(Res.string.desktop_seconds_suffix)}",
                    style = typography.labelSmall,
                    color = colors.standbyAccent
                )
            }

            Spacer(modifier = Modifier.height(spacing.extraSmall))

            LinearProgressIndicator(
                progress = { (state.tokenRemainingSeconds / 120f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = colors.standbyAccent,
                trackColor = colors.surfaceElevated
            )
        }

        Spacer(modifier = Modifier.height(spacing.large))

        // Botón de refresco manual
        Button(
            onClick = onRefreshToken,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surfaceElevated,
                contentColor = colors.standbyAccent
            )
        ) {
            Text(
                text = stringResource(Res.string.desktop_action_refresh_qr),
                style = typography.labelSmall
            )
        }
    }
}
