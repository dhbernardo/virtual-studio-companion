package com.vcompanion.android.presentation.viewmodel

import com.vcompanion.shared.core.domain.model.PairingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QrScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shouldInitializeWithDefaultScanningState() {
        val viewModel = QrScannerViewModel()
        val state = viewModel.uiState.value

        assertTrue(state.isScanning)
        assertFalse(state.isPermissionGranted)
        assertFalse(state.isPermanentlyDenied)
        assertNull(state.scannedConfig)
    }

    @Test
    fun shouldUpdateStateWhenPermissionGranted() {
        val viewModel = QrScannerViewModel()
        viewModel.onPermissionResult(isGranted = true, isPermanentlyDenied = false)

        val state = viewModel.uiState.value
        assertTrue(state.isPermissionGranted)
        assertFalse(state.isPermanentlyDenied)
    }

    @Test
    fun shouldUpdateStateWhenPermissionPermanentlyDenied() {
        val viewModel = QrScannerViewModel()
        viewModel.onPermissionResult(isGranted = false, isPermanentlyDenied = true)

        val state = viewModel.uiState.value
        assertFalse(state.isPermissionGranted)
        assertTrue(state.isPermanentlyDenied)
    }

    @Test
    fun shouldStoreConfigAndPauseScanningWhenQrCodeDetected() {
        val viewModel = QrScannerViewModel()
        val config = PairingConfig(
            host = "192.168.1.50",
            port = 9000,
            sessionToken = "tok_test_123"
        )

        viewModel.onQrCodeScanned(config)

        val state = viewModel.uiState.value
        assertFalse(state.isScanning)
        assertEquals(config, state.scannedConfig)
    }

    @Test
    fun shouldResetStateWhenResumeScanningCalled() {
        val viewModel = QrScannerViewModel()
        val config = PairingConfig(
            host = "192.168.1.50",
            port = 9000,
            sessionToken = "tok_test_123"
        )
        viewModel.onQrCodeScanned(config)
        viewModel.resetScanning()

        val state = viewModel.uiState.value
        assertTrue(state.isScanning)
        assertNull(state.scannedConfig)
    }
}
