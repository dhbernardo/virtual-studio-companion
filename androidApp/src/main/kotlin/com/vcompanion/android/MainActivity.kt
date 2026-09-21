package com.vcompanion.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vcompanion.android.presentation.ui.CameraScreen
import com.vcompanion.android.presentation.ui.QrScannerScreen
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.designsystem.theme.StudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudioTheme {
                var activeConfig by remember { mutableStateOf<PairingConfig?>(null) }

                val config = activeConfig
                if (config == null) {
                    QrScannerScreen(
                        onQrCodeDetected = { detectedConfig ->
                            activeConfig = detectedConfig
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CameraScreen(
                        config = config,
                        onDisconnect = {
                            activeConfig = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
