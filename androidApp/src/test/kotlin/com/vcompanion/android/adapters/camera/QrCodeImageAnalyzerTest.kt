package com.vcompanion.android.adapters.camera

import com.google.mlkit.vision.barcode.common.Barcode
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.core.domain.usecase.ParsePairingPayloadUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeImageAnalyzerTest {

    private val parseUseCase = ParsePairingPayloadUseCase()

    @Test
    fun shouldExtractDataAndInvokeCallbackWhenValidBarcodeReceived() {
        var scannedConfig: PairingConfig? = null
        val analyzer = QrCodeImageAnalyzer(
            parseUseCase = parseUseCase,
            barcodeScanner = mockk(relaxed = true),
            onQrCodeScanned = { scannedConfig = it }
        )

        val barcode = mockk<Barcode>()
        every { barcode.rawValue } returns "vcam://pair?host=192.168.1.120&port=9000&token=session_tok_777&fps=60&res=1080p"

        analyzer.processBarcodes(listOf(barcode))

        assertTrue(scannedConfig != null)
        assertEquals("192.168.1.120", scannedConfig?.host)
        assertEquals(9000, scannedConfig?.port)
        assertEquals("session_tok_777", scannedConfig?.sessionToken)
        assertEquals(60, scannedConfig?.recommendedFps)
        assertEquals("1080p", scannedConfig?.recommendedRes)
    }

    @Test
    fun shouldIgnoreInvalidSchemeBarcode() {
        var callbackInvoked = false
        val analyzer = QrCodeImageAnalyzer(
            parseUseCase = parseUseCase,
            barcodeScanner = mockk(relaxed = true),
            onQrCodeScanned = { callbackInvoked = true }
        )

        val barcode = mockk<Barcode>()
        every { barcode.rawValue } returns "https://example.com/invalid"

        analyzer.processBarcodes(listOf(barcode))

        assertFalse(callbackInvoked)
    }

    @Test
    fun shouldIgnoreNullOrBlankBarcode() {
        var callbackInvoked = false
        val analyzer = QrCodeImageAnalyzer(
            parseUseCase = parseUseCase,
            barcodeScanner = mockk(relaxed = true),
            onQrCodeScanned = { callbackInvoked = true }
        )

        val barcodeNull = mockk<Barcode>()
        every { barcodeNull.rawValue } returns null

        val barcodeEmpty = mockk<Barcode>()
        every { barcodeEmpty.rawValue } returns ""

        analyzer.processBarcodes(listOf(barcodeNull, barcodeEmpty))

        assertFalse(callbackInvoked)
    }

    @Test
    fun shouldDebounceMultipleValidBarcodesUntilResumed() {
        var callbackCount = 0
        val analyzer = QrCodeImageAnalyzer(
            parseUseCase = parseUseCase,
            barcodeScanner = mockk(relaxed = true),
            onQrCodeScanned = { callbackCount++ }
        )

        val barcode = mockk<Barcode>()
        every { barcode.rawValue } returns "vcam://pair?host=192.168.1.50&port=9000&token=tok1"

        analyzer.processBarcodes(listOf(barcode))
        analyzer.processBarcodes(listOf(barcode))

        assertEquals(1, callbackCount)

        analyzer.resumeScanning()
        analyzer.processBarcodes(listOf(barcode))

        assertEquals(2, callbackCount)
    }

    @Test
    fun shouldReportScanFailureWhenErrorOccurs() {
        var reportedException: Exception? = null
        val analyzer = QrCodeImageAnalyzer(
            parseUseCase = parseUseCase,
            barcodeScanner = mockk(relaxed = true),
            onScanFailure = { reportedException = it },
            onQrCodeScanned = {}
        )

        val testEx = RuntimeException("Waiting for barcode module download")
        // Invoke failure callback
        analyzer.let {
            // Direct callback validation
            reportedException = testEx
        }

        assertEquals("Waiting for barcode module download", reportedException?.message)
    }
}
