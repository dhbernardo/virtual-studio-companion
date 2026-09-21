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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.vcompanion.shared.resources.qr_scanner_overlay_hint
import com.vcompanion.shared.resources.qr_scanner_searching
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
                            QrCodeImageAnalyzer { pairingConfig ->
                                viewModel.onQrCodeScanned(pairingConfig)
                            }
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
                    // Log or handle camera binding failure
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                cameraExecutor.shutdown()
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

            // Bottom searching indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = StudioTheme.spacing.screenEdge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.qr_scanner_searching),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
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
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
        )
    }
}
