package com.vcompanion.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vcompanion.desktop.adapters.inbound.KtorServerGateway
import com.vcompanion.desktop.adapters.outbound.ObsWebSocketAdapter
import com.vcompanion.desktop.presentation.ui.MainWindow
import com.vcompanion.desktop.presentation.viewmodel.DesktopHostViewModel
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.desktop_window_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    val windowState = rememberWindowState(width = 900.dp, height = 700.dp)

    val gateway = remember { KtorServerGateway() }
    val obsConnector = remember { ObsWebSocketAdapter() }
    val viewModel = remember {
        val port = runBlocking { gateway.start(8080, 8090) }
        DesktopHostViewModel(
            gateway = gateway,
            obsConnector = obsConnector,
            effectivePort = port
        )
    }

    Window(
        onCloseRequest = {
            viewModel.onClose()
            exitApplication()
        },
        state = windowState,
        title = stringResource(Res.string.desktop_window_title)
    ) {
        MainWindow(viewModel = viewModel)
    }
}
