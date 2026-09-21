package com.vcompanion.desktop.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.vcompanion.desktop.presentation.viewmodel.DesktopHostViewModel
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.designsystem.theme.StudioTheme

/**
 * Contenido principal de la ventana Compose Desktop.
 * Conmuta entre la vista de emparejamiento con QR [QrPairingView] y el dashboard [StreamingDashboardView]
 * según el estado reactivo de la FSM (RF-002, RF-004, RNF-004).
 */
@Composable
fun MainWindow(
    viewModel: DesktopHostViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    StudioTheme {
        Box(modifier = modifier.fillMaxSize()) {
            when (state.connectionState) {
                is ConnectionState.Disconnected,
                is ConnectionState.Discovering,
                is ConnectionState.Pairing,
                is ConnectionState.Error -> {
                    QrPairingView(
                        state = state,
                        onRefreshToken = { viewModel.refreshToken() },
                        onConnectObs = { viewModel.connectObs() }
                    )
                }

                is ConnectionState.Connected,
                is ConnectionState.Streaming,
                is ConnectionState.Reconnecting -> {
                    StreamingDashboardView(
                        state = state,
                        onSendCommand = { viewModel.sendCommand(it) },
                        onDisconnect = { viewModel.disconnectSession() },
                        onConnectObs = { viewModel.connectObs() }
                    )
                }
            }
        }
    }
}
