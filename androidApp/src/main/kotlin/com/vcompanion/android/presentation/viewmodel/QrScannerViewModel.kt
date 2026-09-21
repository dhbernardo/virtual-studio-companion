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
    val scannedConfig: PairingConfig? = null,
    val errorMessage: String? = null,
    val isManualEntryDialogVisible: Boolean = false
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
                scannedConfig = config,
                errorMessage = null
            )
        }
    }

    fun onScanError(message: String) {
        _uiState.update { current ->
            current.copy(errorMessage = message)
        }
    }

    fun setManualEntryDialogVisible(visible: Boolean) {
        _uiState.update { current ->
            current.copy(isManualEntryDialogVisible = visible)
        }
    }

    fun onManualPairingConfig(host: String, portStr: String, token: String): Boolean {
        val cleanHost = host.trim()
        val port = portStr.trim().toIntOrNull()
        val cleanToken = token.trim()

        if (cleanHost.isBlank() || port == null || port <= 0 || port > 65535 || cleanToken.isBlank()) {
            return false
        }

        val config = PairingConfig(
            host = cleanHost,
            port = port,
            sessionToken = cleanToken
        )

        _uiState.update { current ->
            current.copy(
                isScanning = false,
                scannedConfig = config,
                isManualEntryDialogVisible = false,
                errorMessage = null
            )
        }
        return true
    }

    fun consumeScannedConfig() {
        _uiState.update { current ->
            current.copy(
                isScanning = true,
                scannedConfig = null,
                errorMessage = null
            )
        }
    }

    fun resetScanning() {
        _uiState.update { current ->
            current.copy(
                isScanning = true,
                scannedConfig = null,
                errorMessage = null
            )
        }
    }
}
