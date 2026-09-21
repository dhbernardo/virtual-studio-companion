package com.vcompanion.android.presentation.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vcompanion.android.adapters.camera.CameraXCaptureAdapter
import com.vcompanion.android.adapters.camera.StreamResolution
import com.vcompanion.android.adapters.network.KtorClientStreamAdapter
import com.vcompanion.android.adapters.telemetry.AndroidTelemetryProvider
import com.vcompanion.android.presentation.ui.components.CameraPreviewView
import com.vcompanion.android.presentation.viewmodel.CameraStreamViewModel
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.designsystem.components.CameraControlBar
import com.vcompanion.shared.designsystem.components.StreamStatus
import com.vcompanion.shared.designsystem.components.StudioBadge
import com.vcompanion.shared.designsystem.components.StudioIndicator
import com.vcompanion.shared.designsystem.components.TelemetryPill
import com.vcompanion.shared.designsystem.theme.StudioTheme
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.action_disconnect_hud
import com.vcompanion.shared.resources.camera_hud_zoom
import com.vcompanion.shared.resources.thermal_alert_warning
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun CameraScreen(
    config: PairingConfig,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val streamAdapter = remember { KtorClientStreamAdapter() }
    val telemetryProvider = remember { AndroidTelemetryProvider(context, pollIntervalMs = 500L) }
    val captureAdapter = remember {
        CameraXCaptureAdapter(
            targetResolution = StreamResolution.FHD_1080P,
            targetFps = config.recommendedFps
        )
    }

    val viewModel = remember {
        CameraStreamViewModel(
            streamGateway = streamAdapter,
            telemetryEmitter = telemetryProvider,
            hasFlashUnit = true,
            onApplyZoom = { ratio -> captureAdapter.applyZoomRatio(ratio) },
            onToggleTorch = { captureAdapter.toggleTorch(hasFlashUnit = true) },
            onSetFps = { fps -> captureAdapter.setTargetFps(fps) }
        )
    }

    val uiState by viewModel.uiState.collectAsState()

    // FLAG_KEEP_SCREEN_ON handling (RF-003)
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Lifecycle observer to handle app backgrounding (RF-005)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                captureAdapter.stopCapture()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Back handler and exit cleanup (RF-005)
    BackHandler {
        viewModel.disconnect("USER_BACK_PRESSED")
        captureAdapter.release()
        onDisconnect()
    }

    // Start session on entry
    LaunchedEffect(config) {
        streamAdapter.connect(
            host = config.host,
            port = config.port,
            sessionToken = config.sessionToken
        )
        viewModel.startSession(config)
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(previewViewRef) {
        val previewView = previewViewRef
        if (previewView != null) {
            captureAdapter.startCapture(
                context = context,
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = previewView.surfaceProvider,
                onFrameEncoded = { encodedBytes ->
                    coroutineScope.launch {
                        streamAdapter.sendVideoFrame(encodedBytes)
                    }
                }
            )
        }
        onDispose {
            captureAdapter.stopCapture()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        CameraPreviewView(
            onPreviewCreated = { previewViewRef = it },
            modifier = Modifier.fillMaxSize()
        )

        // Top Broadcast HUD Bar (StudioBadge, StudioIndicator, TelemetryPill, Disconnect)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(
                    top = StudioTheme.spacing.screenEdge,
                    start = StudioTheme.spacing.large,
                    end = StudioTheme.spacing.large
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StudioTheme.spacing.small)
            ) {
                val badgeStatus = if (uiState.isThermalAlertActive) {
                    StreamStatus.ALERT
                } else {
                    StreamStatus.LIVE
                }
                StudioBadge(status = badgeStatus)
                StudioIndicator(
                    color = if (uiState.isThermalAlertActive) StudioTheme.colors.warningAccent else StudioTheme.colors.liveAccent,
                    isPulsing = true
                )
            }

            TelemetryPill(metrics = uiState.deviceMetrics)

            Button(
                onClick = {
                    viewModel.disconnect("USER_DISCONNECT_CLICK")
                    captureAdapter.release()
                    onDisconnect()
                },
                shape = RoundedCornerShape(StudioTheme.spacing.medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioTheme.colors.surfaceElevated.copy(alpha = 0.85f),
                    contentColor = StudioTheme.colors.liveAccent
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = StudioTheme.spacing.medium,
                    vertical = StudioTheme.spacing.extraSmall
                )
            ) {
                Text(
                    text = stringResource(Res.string.action_disconnect_hud),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Thermal Alert Banner (RF-004)
        AnimatedVisibility(
            visible = uiState.isThermalAlertActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = StudioTheme.spacing.screenEdge + 64.dp, start = StudioTheme.spacing.large, end = StudioTheme.spacing.large)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = StudioTheme.colors.warningAccent.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(StudioTheme.spacing.medium)
                    )
                    .padding(StudioTheme.spacing.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.thermal_alert_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom Controls HUD (CameraControlBar & Zoom level indicator)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = StudioTheme.spacing.section),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom indicator
            Box(
                modifier = Modifier
                    .background(
                        color = StudioTheme.colors.surfaceElevated.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(StudioTheme.spacing.medium)
                    )
                    .padding(horizontal = StudioTheme.spacing.large, vertical = StudioTheme.spacing.extraSmall)
            ) {
                Text(
                    text = "${stringResource(Res.string.camera_hud_zoom)}: ${"%.1f".format(uiState.currentZoomRatio)}x",
                    style = StudioTheme.typography.monoNumeric,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(StudioTheme.spacing.medium))

            CameraControlBar(
                onSwitchCamera = {
                    // Switch front/back lens
                },
                onToggleTorch = {
                    viewModel.toggleTorch()
                },
                onZoomIn = {
                    viewModel.setZoom(minOf(uiState.currentZoomRatio + 0.5f, 10.0f))
                },
                onZoomOut = {
                    viewModel.setZoom(maxOf(uiState.currentZoomRatio - 0.5f, 1.0f))
                },
                isTorchOn = uiState.isTorchEnabled,
                isTorchEnabled = uiState.hasFlashUnit,
                isZoomInEnabled = uiState.currentZoomRatio < 10.0f,
                isZoomOutEnabled = uiState.currentZoomRatio > 1.0f
            )
        }
    }
}
