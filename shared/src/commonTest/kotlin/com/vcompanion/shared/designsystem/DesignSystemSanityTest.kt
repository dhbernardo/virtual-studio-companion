package com.vcompanion.shared.designsystem

import com.vcompanion.shared.core.domain.model.ConnectionError
import com.vcompanion.shared.core.domain.model.ConnectionErrorCode
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.designsystem.components.StreamStatus
import com.vcompanion.shared.designsystem.components.TelemetryFormatter
import com.vcompanion.shared.designsystem.components.toStreamStatus
import com.vcompanion.shared.designsystem.theme.ColorContrastCalculator
import com.vcompanion.shared.designsystem.theme.StudioDarkColors
import com.vcompanion.shared.designsystem.theme.StudioLightColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T03: Pruebas unitarias de contraste WCAG AA, formateo y mapeo de estados.
 * Valida RF-001, RF-002, RF-003 y RNF-003.
 */
class DesignSystemSanityTest {

    @Test
    fun shouldEnsureWcagAaContrastInDarkModeTokens() {
        // TextPrimary sobre Surface (mínimo 4.5:1)
        val textPrimaryContrast = ColorContrastCalculator.calculateContrastRatio(
            StudioDarkColors.textPrimary,
            StudioDarkColors.surface
        )
        assertTrue(
            textPrimaryContrast >= 4.5,
            "Dark TextPrimary contrast failed: $textPrimaryContrast"
        )

        // TextSecondary sobre Surface (mínimo 4.5:1)
        val textSecondaryContrast = ColorContrastCalculator.calculateContrastRatio(
            StudioDarkColors.textSecondary,
            StudioDarkColors.surface
        )
        assertTrue(
            textSecondaryContrast >= 4.5,
            "Dark TextSecondary contrast failed: $textSecondaryContrast"
        )
    }

    @Test
    fun shouldEnsureWcagAaContrastInLightModeTokens() {
        val surface = StudioLightColors.surface

        // TextPrimary sobre Surface (mínimo 4.5:1)
        val primaryContrast = ColorContrastCalculator.calculateContrastRatio(
            StudioLightColors.textPrimary,
            surface
        )
        assertTrue(primaryContrast >= 4.5, "Light TextPrimary contrast failed: $primaryContrast")

        // TextSecondary sobre Surface (mínimo 4.5:1)
        val secondaryContrast = ColorContrastCalculator.calculateContrastRatio(
            StudioLightColors.textSecondary,
            surface
        )
        assertTrue(secondaryContrast >= 4.5, "Light TextSecondary contrast failed: $secondaryContrast")

        // Acentos sobre Surface (mínimo 4.5:1 para legibilidad)
        val liveContrast = ColorContrastCalculator.calculateContrastRatio(
            StudioLightColors.liveAccent,
            surface
        )
        assertTrue(liveContrast >= 4.5, "Light LiveAccent contrast failed: $liveContrast")

        val standbyContrast = ColorContrastCalculator.calculateContrastRatio(
            StudioLightColors.standbyAccent,
            surface
        )
        assertTrue(standbyContrast >= 4.5, "Light StandbyAccent contrast failed: $standbyContrast")

        val warningContrast = ColorContrastCalculator.calculateContrastRatio(
            StudioLightColors.warningAccent,
            surface
        )
        assertTrue(warningContrast >= 4.5, "Light WarningAccent contrast failed: $warningContrast")
    }

    @Test
    fun shouldMapConnectionStateToStreamStatusCorrectly() {
        val sampleConfig = PairingConfig("192.168.1.10", 8080, "tok")

        assertEquals(StreamStatus.OFFLINE, ConnectionState.Disconnected.toStreamStatus())
        assertEquals(StreamStatus.STANDBY, ConnectionState.Discovering.toStreamStatus())
        assertEquals(StreamStatus.STANDBY, ConnectionState.Pairing(sampleConfig).toStreamStatus())
        assertEquals(StreamStatus.STANDBY, ConnectionState.Connected().toStreamStatus())
        assertEquals(StreamStatus.LIVE, ConnectionState.Streaming.toStreamStatus())
        assertEquals(StreamStatus.ALERT, ConnectionState.Reconnecting().toStreamStatus())
        assertEquals(
            StreamStatus.ALERT,
            ConnectionState.Error(ConnectionError(ConnectionErrorCode.NETWORK_LOST)).toStreamStatus()
        )
    }

    @Test
    fun shouldFormatTelemetryMetricsWithPlaceholdersAndUnits() {
        // Placeholder cuando es nulo
        assertEquals("--- ms / -- FPS", TelemetryFormatter.format(null))

        // Formato con métricas válidas
        val metrics = DeviceMetrics(
            fps = 60.0f,
            bitrateKbps = 8500L,
            latencyMs = 42L,
            batteryLevel = 90,
            isCharging = false
        )
        assertEquals("42 ms / 60 FPS", TelemetryFormatter.format(metrics))
    }

    @Test
    fun shouldResolveSemanticLatencyColorAccordingToThresholds() {
        val colors = StudioDarkColors

        // <= 50ms -> standbyAccent (verde)
        assertEquals(colors.standbyAccent, TelemetryFormatter.getLatencyColor(30L, colors))
        assertEquals(colors.standbyAccent, TelemetryFormatter.getLatencyColor(50L, colors))

        // 51ms..120ms -> warningAccent (ámbar)
        assertEquals(colors.warningAccent, TelemetryFormatter.getLatencyColor(51L, colors))
        assertEquals(colors.warningAccent, TelemetryFormatter.getLatencyColor(120L, colors))

        // > 120ms -> liveAccent (rojo de alerta)
        assertEquals(colors.liveAccent, TelemetryFormatter.getLatencyColor(121L, colors))
        assertEquals(colors.liveAccent, TelemetryFormatter.getLatencyColor(300L, colors))

        // null -> textSecondary
        assertEquals(colors.textSecondary, TelemetryFormatter.getLatencyColor(null, colors))
    }
}
