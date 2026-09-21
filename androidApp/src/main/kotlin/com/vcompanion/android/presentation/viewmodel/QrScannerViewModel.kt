package com.vcompanion.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.vcompanion.shared.core.domain.model.PairingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class QrScannerUiState(
    val isScanning: Boolean = true,
    val isPermissionGranted: Boolean = false,
    val isPermanentlyDenied: Boolean = false,
    val scannedConfig: PairingConfig? = null
)

class QrScannerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()

    fun onPermissionResult(isGranted: Boolean, isPermanentlyDenied: Boolean = false) {
        _uiState.update { current ->
            current.copy(
                isPermissionGranted = isGranted,
                isPermanentlyDenied = if (isGranted) false else isPermanentlyDenied
            )
        }
    }

    fun onQrCodeScanned(config: PairingConfig) {
        _uiState.update { current ->
            current.copy(
                isScanning = false,
                scannedConfig = config
            )
        }
    }

    fun resetScanning() {
        _uiState.update { current ->
            current.copy(
                isScanning = true,
                scannedConfig = null
            )
        }
    }
}
