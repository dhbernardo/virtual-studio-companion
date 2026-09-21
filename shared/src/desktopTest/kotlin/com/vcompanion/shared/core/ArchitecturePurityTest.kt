package com.vcompanion.shared.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T07: Verificación estricta de pureza arquitectónica y ausencia de imports de plataforma.
 * Valida RNF-001 y el Principio 1 de docs/constitution.md.
 */
class ArchitecturePurityTest {

    private val forbiddenImportPrefixes = listOf(
        "import android.",
        "import java.",
        "import kotlinx.cinterop."
    )

    @Test
    fun shouldEnsureAbsolutePlatformPurityInCommonMain() {
        val commonMainDir = File("src/commonMain/kotlin")
        val effectiveDir = if (commonMainDir.exists()) {
            commonMainDir
        } else {
            File("shared/src/commonMain/kotlin")
        }

        assertTrue(effectiveDir.exists() && effectiveDir.isDirectory, "Directorio commonMain no encontrado: ${effectiveDir.absolutePath}")

        val ktFiles = effectiveDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(ktFiles.isNotEmpty(), "No se encontraron archivos Kotlin en ${effectiveDir.absolutePath}")

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                for (forbidden in forbiddenImportPrefixes) {
                    if (trimmed.startsWith(forbidden)) {
                        violations.add("${file.name}:${index + 1} -> '$trimmed'")
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Se detectaron violaciones a RNF-001 (imports de plataforma prohibidos):\n${violations.joinToString("\n")}"
        )

        println("T07 Purity Check: ${ktFiles.size} archivos Kotlin analizados en commonMain. Cero violaciones detectadas.")
    }
}
