package com.vcompanion.desktop.presentation.ui

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas unitarias para la generación de matrices QR con ZXing Core (RF-002, ISO/IEC 18004).
 */
class QrCanvasRendererTest {

    @Test
    fun shouldGenerateAndDecodePairingPayloadRoundTrip() {
        val payload = "vcam://pair?host=192.168.1.121&port=8080&token=abcdef1234567890abcdef1234567890&fps=60&res=1080p"
        val matrix = generateQrMatrix(payload)

        assertNotNull(matrix, "La matriz generada no debe ser nula")
        assertTrue(matrix.isNotEmpty(), "La matriz no debe estar vacía")
        assertEquals(matrix.size, matrix[0].size, "La matriz debe ser perfectamente cuadrada")

        // Reconstruir BitMatrix para verificación con el decodificador oficial de ZXing
        val width = matrix[0].size
        val height = matrix.size
        val bitMatrix = BitMatrix(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (matrix[y][x]) {
                    bitMatrix.set(x, y)
                }
            }
        }

        val decoder = Decoder()
        val decoderResult = decoder.decode(bitMatrix)

        assertEquals(payload, decoderResult.text, "El payload decodificado debe ser idéntico al original")
    }

    @Test
    fun shouldReturnNullForBlankPayload() {
        assertNull(generateQrMatrix(""))
        assertNull(generateQrMatrix("   "))
    }
}
