package com.vcompanion.desktop.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Genera la matriz booleana de módulos de un Código QR (ISO/IEC 18004) mediante ZXing Core.
 * true = módulo oscuro (negro), false = módulo claro (blanco).
 */
fun generateQrMatrix(content: String): Array<BooleanArray>? {
    if (content.isBlank()) return null
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        Array(height) { y ->
            BooleanArray(width) { x ->
                bitMatrix.get(x, y)
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Renderizador de Código QR en Compose Desktop sobre un Canvas vectorial nítido (RF-002, ISO/IEC 18004).
 */
@Composable
fun QrCodeCanvas(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White
) {
    val matrix = remember(payload) {
        generateQrMatrix(payload)
    }

    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize().aspectRatio(1f)) {
            drawRect(color = lightColor)
            if (matrix != null && matrix.isNotEmpty()) {
                val moduleSize = this.size.width / matrix.size
                for (r in matrix.indices) {
                    for (c in matrix[r].indices) {
                        if (matrix[r][c]) {
                            drawRect(
                                color = darkColor,
                                topLeft = Offset(c * moduleSize, r * moduleSize),
                                size = Size(moduleSize + 0.5f, moduleSize + 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
