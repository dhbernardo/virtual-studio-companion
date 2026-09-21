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

/**
 * Generador autocontenido y ligero de matriz de Código QR (Model 2, Byte Mode, ECC Level M).
 * Genera una matriz booleana (true = módulo oscuro / negro, false = módulo claro / blanco).
 */
object QrMatrixGenerator {

    // Tablas GF(256) con polinomio 0x11D
    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            exp[i + 255] = x
            log[x] = i
            x = (x shl 1) xor if (x and 0x80 != 0) 0x11D else 0
        }
        log[0] = 0
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return exp[log[x] + log[y]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val factor = exp[i]
            for (j in poly.indices) {
                next[j] = next[j] xor gfMul(poly[j], factor)
                next[j + 1] = next[j + 1] xor poly[j]
            }
            poly = next
        }
        return poly
    }

    private fun rsComputeEcc(data: ByteArray, eccCount: Int): ByteArray {
        val gen = rsGeneratorPoly(eccCount)
        val res = IntArray(eccCount)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor res[0]
            for (i in 0 until eccCount - 1) {
                res[i] = res[i + 1] xor gfMul(gen[i + 1], factor)
            }
            res[eccCount - 1] = gfMul(gen[eccCount], factor)
        }
        return ByteArray(eccCount) { res[it].toByte() }
    }

    /**
     * Parámetros de versión QR para ECC Level M (Model 2):
     * Version 6: 41x41 módulos, 172 palabras código totales (108 de datos, 64 de ECC divididos en 4 bloques de 16).
     */
    fun generate(content: String): Array<BooleanArray> {
        val rawBytes = content.encodeToByteArray()
        val dataLen = rawBytes.size

        // Version 6: 41x41, soporta hasta 106 bytes en modo byte con ECC M
        val version = 6
        val size = 17 + 4 * version // 41
        val totalDataCodewords = 108
        val eccPerBlock = 16
        val numBlocks = 4

        // 1. Bit buffer: Modo Byte (0100) + Longitud (8 bits para V1-9) + Datos + Terminador (0000)
        val bitBuffer = mutableListOf<Int>()
        fun putBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bitBuffer.add((value shr i) and 1)
            }
        }

        putBits(0b0100, 4) // Byte mode indicator
        putBits(dataLen, 8) // Length indicator
        for (b in rawBytes) {
            putBits(b.toInt() and 0xFF, 8)
        }
        // Terminator
        val termLen = minOf(4, totalDataCodewords * 8 - bitBuffer.size)
        putBits(0, termLen)
        // Pad to byte
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(0)
        }
        // Pad bytes (0xEC, 0x11)
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.size < totalDataCodewords * 8) {
            putBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        val dataCodewords = ByteArray(totalDataCodewords)
        for (i in 0 until totalDataCodewords) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bitBuffer[i * 8 + j]
            }
            dataCodewords[i] = b.toByte()
        }

        // 2. Reed-Solomon en 4 bloques (cada uno de 27 bytes de datos + 16 ECC = 43)
        val blockSize = totalDataCodewords / numBlocks // 27
        val blocks = Array(numBlocks) { i ->
            dataCodewords.sliceArray(i * blockSize until (i + 1) * blockSize)
        }
        val eccBlocks = Array(numBlocks) { i ->
            rsComputeEcc(blocks[i], eccPerBlock)
        }

        // 3. Interleave codewords
        val finalCodewords = mutableListOf<Byte>()
        for (i in 0 until blockSize) {
            for (b in 0 until numBlocks) {
                finalCodewords.add(blocks[b][i])
            }
        }
        for (i in 0 until eccPerBlock) {
            for (b in 0 until numBlocks) {
                finalCodewords.add(eccBlocks[b][i])
            }
        }

        // 4. Matrix layout
        val matrix = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        // Finder patterns
        fun placeFinder(top: Int, left: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val row = top + r
                    val col = left + c
                    if (row in 0 until size && col in 0 until size) {
                        isFunction[row][col] = true
                        if (r in 0..6 && c in 0..6) {
                            matrix[row][col] = (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
                        } else {
                            matrix[row][col] = false
                        }
                    }
                }
            }
        }
        placeFinder(0, 0)
        placeFinder(0, size - 7)
        placeFinder(size - 7, 0)

        // Alignment pattern for V6 at (34, 34)
        fun placeAlignment(centerR: Int, centerC: Int) {
            for (r in -2..2) {
                for (c in -2..2) {
                    val row = centerR + r
                    val col = centerC + c
                    if (!isFunction[row][col]) {
                        isFunction[row][col] = true
                        matrix[row][col] = (r == -2 || r == 2 || c == -2 || c == 2 || (r == 0 && c == 0))
                    }
                }
            }
        }
        placeAlignment(34, 34)

        // Timing patterns
        for (i in 8 until size - 8) {
            if (!isFunction[6][i]) {
                isFunction[6][i] = true
                matrix[6][i] = (i % 2 == 0)
            }
            if (!isFunction[i][6]) {
                isFunction[i][6] = true
                matrix[i][6] = (i % 2 == 0)
            }
        }

        // Dark module
        isFunction[4 * version + 9][8] = true
        matrix[4 * version + 9][8] = true

        // Reserve format info area
        for (i in 0..8) {
            if (i < size) {
                isFunction[8][i] = true
                isFunction[i][8] = true
                isFunction[8][size - 1 - i] = true
                isFunction[size - 1 - i][8] = true
            }
        }

        // 5. Place data bits using standard zigzag layout with Mask 0 ((r+c)%2 == 0)
        val allBits = mutableListOf<Int>()
        for (cw in finalCodewords) {
            val v = cw.toInt() and 0xFF
            for (b in 7 downTo 0) {
                allBits.add((v shr b) and 1)
            }
        }

        var bitIdx = 0
        var upward = true
        var col = size - 1
        while (col > 0) {
            if (col == 6) col-- // Skip timing col
            val rows = if (upward) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (c in 0..1) {
                    val actualCol = col - c
                    if (!isFunction[row][actualCol]) {
                        val bit = if (bitIdx < allBits.size) allBits[bitIdx++] else 0
                        // Mask 0: (row + actualCol) % 2 == 0
                        val mask = (row + actualCol) % 2 == 0
                        matrix[row][actualCol] = if (mask) bit == 0 else bit == 1
                    }
                }
            }
            upward = !upward
            col -= 2
        }

        // 6. Format info for ECC Level M (00) and Mask 0 (000) -> Format bits: 101010000010010
        val formatBits = intArrayOf(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0)
        // Top-left
        for (i in 0..5) matrix[8][i] = formatBits[i] == 1
        matrix[8][7] = formatBits[6] == 1
        matrix[8][8] = formatBits[7] == 1
        matrix[7][8] = formatBits[8] == 1
        for (i in 9..14) matrix[14 - i][8] = formatBits[i] == 1

        // Other corners
        for (i in 0..7) matrix[size - 1 - i][8] = formatBits[i] == 1
        for (i in 8..14) matrix[8][size - 15 + i] = formatBits[i] == 1

        return matrix
    }
}

/**
 * Renderizador de Código QR en Compose Desktop sobre un Canvas vectorial nítido.
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
        if (payload.isNotBlank()) {
            try {
                QrMatrixGenerator.generate(payload)
            } catch (_: Exception) {
                null
            }
        } else null
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
