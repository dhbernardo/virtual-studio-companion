package com.vcompanion.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.app_name
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    val windowState = rememberWindowState(width = 800.dp, height = 600.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Virtual Studio Companion Host"
    ) {
        MaterialTheme {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(Res.string.app_name))
            }
        }
    }
}
