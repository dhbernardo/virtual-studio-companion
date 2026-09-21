package com.vcompanion.shared.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Calculador matemático de contraste WCAG 2.1 para garantizar accesibilidad AA (>4.5:1).
 * Cumple con RNF-003 y RF-001.
 */
object ColorContrastCalculator {

    fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val lum1 = calculateRelativeLuminance(foreground)
        val lum2 = calculateRelativeLuminance(background)

        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)

        return (lighter + 0.05) / (darker + 0.05)
    }

    fun isWcagAaCompliant(foreground: Color, background: Color, minimumRatio: Double = 4.5): Boolean {
        return calculateContrastRatio(foreground, background) >= minimumRatio
    }

    private fun calculateRelativeLuminance(color: Color): Double {
        val r = transformComponent(color.red.toDouble())
        val g = transformComponent(color.green.toDouble())
        val b = transformComponent(color.blue.toDouble())

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun transformComponent(c: Double): Double {
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }
}
