package com.vcompanion.android.presentation.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vcompanion.android.adapters.camera.QrCodeImageAnalyzer
import com.vcompanion.android.presentation.ui.components.CameraPreviewView
import com.vcompanion.android.presentation.viewmodel.QrScannerViewModel
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.designsystem.theme.StudioTheme
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.qr_scanner_manual_cancel_button
import com.vcompanion.shared.resources.qr_scanner_manual_connect_button
import com.vcompanion.shared.resources.qr_scanner_manual_dialog_title
import com.vcompanion.shared.resources.qr_scanner_manual_entry_action
import com.vcompanion.shared.resources.qr_scanner_manual_error_invalid
import com.vcompanion.shared.resources.qr_scanner_manual_host_label
import com.vcompanion.shared.resources.qr_scanner_manual_port_label
import com.vcompanion.shared.resources.qr_scanner_manual_token_label
import com.vcompanion.shared.resources.qr_scanner_overlay_hint
import com.vcompanion.shared.resources.qr_scanner_searching
import com.vcompanion.shared.resources.qr_scanner_status_error
import com.vcompanion.shared.resources.qr_scanner_title
import org.jetbrains.compose.resources.stringResource
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    onQrCodeDetected: (PairingConfig) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context as? Activity
        val isPermanentlyDenied = if (!isGranted && activity != null) {
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.CAMERA
            )
        } else {
            false
        }
        viewModel.onPermissionResult(isGranted, isPermanentlyDenied)
    }

    LaunchedEffect(Unit) {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            viewModel.onPermissionResult(isGranted = true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.scannedConfig) {
        uiState.scannedConfig?.let { config ->
            onQrCodeDetected(config)
        }
    }

    if (!uiState.isPermissionGranted) {
        PermissionScreen(
            isPermanentlyDenied = uiState.isPermanentlyDenied,
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = modifier
        )
    } else {
        var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

        DisposableEffect(Unit) {
            onDispose {
                cameraExecutor.shutdown()
            }
        }

        DisposableEffect(lifecycleOwner, previewViewRef) {
            val previewView = previewViewRef
            if (previewView == null) return@DisposableEffect onDispose {}

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            cameraExecutor,
                            QrCodeImageAnalyzer(
                                onScanFailure = { exception ->
                                    viewModel.onScanError(exception.localizedMessage ?: "")
                                },
                                onQrCodeScanned = { pairingConfig ->
                                    viewModel.onQrCodeScanned(pairingConfig)
                                }
                            )
                        )
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (_: Exception) {
                    // Camera binding failure handled gracefully
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                } catch (_: Exception) {
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            CameraPreviewView(
                onPreviewCreated = { previewViewRef = it },
                modifier = Modifier.fillMaxSize()
            )

            // Scanner overlay with darkened cut-out
            ScannerOverlay(
                modifier = Modifier.fillMaxSize()
            )

            // Header and hint texts
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(
                        top = StudioTheme.spacing.screenEdge,
                        start = StudioTheme.spacing.large,
                        end = StudioTheme.spacing.large
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.qr_scanner_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(StudioTheme.spacing.small))

                Text(
                    text = stringResource(Res.string.qr_scanner_overlay_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            // Bottom searching indicator and manual entry button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = StudioTheme.spacing.screenEdge,
                        start = StudioTheme.spacing.large,
                        end = StudioTheme.spacing.large
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val statusText = if (uiState.errorMessage != null) {
                    stringResource(Res.string.qr_scanner_status_error)
                } else {
                    stringResource(Res.string.qr_scanner_searching)
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.errorMessage != null) StudioTheme.colors.warningAccent else Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(StudioTheme.spacing.medium))

                Button(
                    onClick = { viewModel.setManualEntryDialogVisible(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioTheme.colors.surfaceElevated,
                        contentColor = StudioTheme.colors.standbyAccent
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.qr_scanner_manual_entry_action),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (uiState.isManualEntryDialogVisible) {
            ManualPairingDialog(
                onDismiss = { viewModel.setManualEntryDialogVisible(false) },
                onConnect = { host, port, token ->
                    viewModel.onManualPairingConfig(host, port, token)
                }
            )
        }
    }
}

@Composable
private fun ManualPairingDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, String) -> Unit
) {
    var host by remember { mutableStateOf("192.168.1.121") }
    var port by remember { mutableStateOf("8080") }
    var token by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.qr_scanner_manual_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                color = StudioTheme.colors.textPrimary
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; hasError = false },
                    label = { Text(stringResource(Res.string.qr_scanner_manual_host_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StudioTheme.spacing.small))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it; hasError = false },
                    label = { Text(stringResource(Res.string.qr_scanner_manual_port_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StudioTheme.spacing.small))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; hasError = false },
                    label = { Text(stringResource(Res.string.qr_scanner_manual_token_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasError) {
                    Spacer(modifier = Modifier.height(StudioTheme.spacing.extraSmall))
                    Text(
                        text = stringResource(Res.string.qr_scanner_manual_error_invalid),
                        color = StudioTheme.colors.liveAccent,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanHost = host.trim()
                    val cleanPort = port.trim().toIntOrNull()
                    val cleanToken = token.trim()
                    if (cleanHost.isNotEmpty() && cleanPort != null && cleanPort in 1..65535 && cleanToken.isNotEmpty()) {
                        onConnect(cleanHost, port.trim(), cleanToken)
                    } else {
                        hasError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioTheme.colors.standbyAccent
                )
            ) {
                Text(stringResource(Res.string.qr_scanner_manual_connect_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.qr_scanner_manual_cancel_button),
                    color = StudioTheme.colors.textSecondary
                )
            }
        },
        containerColor = StudioTheme.colors.surfaceElevated
    )
}

@Composable
private fun ScannerOverlay(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .border(
                    width = 2.dp,
                    color = StudioTheme.colors.standbyAccent,
                    shape = RoundedCornerShape(16.dp)
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cutOutSize = 260.dp.toPx()
            val left = (size.width - cutOutSize) / 2
            val top = (size.height - cutOutSize) / 2

            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                size = size
            )

            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(cutOutSize, cutOutSize),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                blendMode = BlendMode.Clear
            )
        }
    }
}
