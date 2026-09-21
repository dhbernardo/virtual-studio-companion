package com.vcompanion.shared.core

import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.domain.model.ThermalState
import com.vcompanion.shared.core.protocol.CoreJson
import com.vcompanion.shared.core.protocol.ProtocolMessage
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * T07: Benchmark de serialización JSON en memoria (< 2 ms) y verificación de rendimiento.
 * Valida RNF-002 y RNF-003.
 */
class SerializationBenchmarkTest {

    @Test
    fun benchmark1000JsonSerializationsAverageUnder2Ms() {
        val samplePacket = ProtocolMessage.TelemetryPacket(
            timestamp = 1726850000000L,
            metrics = DeviceMetrics(
                fps = 60.0f,
                bitrateKbps = 8500L,
                latencyMs = 42L,
                batteryLevel = 85,
                isCharging = false,
                thermalState = ThermalState.NOMINAL
            )
        )

        // Warmup de la máquina virtual (100 iteraciones)
        repeat(100) {
            val json = CoreJson.encodeToString(ProtocolMessage.serializer(), samplePacket)
            val decoded = CoreJson.decodeFromString(ProtocolMessage.serializer(), json)
            check(decoded == samplePacket)
        }

        // Benchmark de 1.000 operaciones consecutivas
        val iterations = 1000
        val totalDuration = measureTime {
            for (i in 1..iterations) {
                val json = CoreJson.encodeToString(ProtocolMessage.serializer(), samplePacket)
                val decoded = CoreJson.decodeFromString(ProtocolMessage.serializer(), json)
                check(decoded == samplePacket)
            }
        }

        val totalMs = totalDuration.inWholeMicroseconds / 1000.0
        val averageMsPerOp = totalMs / iterations

        println("T07 Benchmark: 1000 serializaciones completadas en ${totalMs} ms (promedio: ${averageMsPerOp} ms/op)")

        // Requisito RNF-003: promedio inferior a 2.0 ms por operación
        assertTrue(
            averageMsPerOp < 2.0,
            "Rendimiento de serialización fuera de límite: ${averageMsPerOp} ms/op (límite: < 2.0 ms)"
        )
    }
}
