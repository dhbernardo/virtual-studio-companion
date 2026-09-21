package com.vcompanion.android.adapters.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.core.domain.usecase.ParsePairingPayloadUseCase
import java.util.concurrent.atomic.AtomicBoolean

class QrCodeImageAnalyzer(
    private val parseUseCase: ParsePairingPayloadUseCase = ParsePairingPayloadUseCase(),
    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    ),
    private val onScanFailure: ((Exception) -> Unit)? = null,
    private val onQrCodeScanned: (PairingConfig) -> Unit
) : ImageAnalysis.Analyzer {

    private val isScanningActive = AtomicBoolean(true)

    fun resumeScanning() {
        isScanningActive.set(true)
    }

    fun pauseScanning() {
        isScanningActive.set(false)
    }

    fun isScanning(): Boolean = isScanningActive.get()

    fun processBarcodes(barcodes: List<Barcode>) {
        if (!isScanningActive.get()) return

        for (barcode in barcodes) {
            val raw = barcode.rawValue ?: continue
            if (raw.isBlank()) continue

            val config = parseUseCase.parseOrNull(raw)
            if (config != null) {
                if (isScanningActive.compareAndSet(true, false)) {
                    onQrCodeScanned(config)
                    break
                }
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isScanningActive.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    processBarcodes(barcodes)
                }
                .addOnFailureListener { exception ->
                    onScanFailure?.invoke(exception)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
