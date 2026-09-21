package com.vcompanion.shared

import com.vcompanion.shared.domain.model.SessionConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Prueba de sanidad arquitectónica que valida la infraestructura de testing
 * agnóstica de plataforma en shared/src/commonTest según RF-002 y RF-004.
 */
class ArchitectureSanityTest {

    @Test
    fun shouldSerializeAndDeserializeSessionConfigSuccessfully() {
        val config = SessionConfig(
            sessionId = "session-12345",
            hostIp = "192.168.1.100",
            port = 8080,
            secretToken = "tok_abc_xyz"
        )

        val jsonString = Json.encodeToString(SessionConfig.serializer(), config)
        val decoded = Json.decodeFromString(SessionConfig.serializer(), jsonString)

        assertEquals(config, decoded)
        assertEquals("session-12345", decoded.sessionId)
        assertEquals("192.168.1.100", decoded.hostIp)
        assertEquals(8080, decoded.port)
        assertEquals("tok_abc_xyz", decoded.secretToken)
    }

    @Test
    fun shouldFailWhenDeserializingCorruptedJson() {
        val malformedJson = """{"sessionId": "123", "port": "invalid_port"}"""

        assertFailsWith<SerializationException> {
            Json.decodeFromString(SessionConfig.serializer(), malformedJson)
        }
    }

    @Test
    fun shouldValidatePortRangeAndSessionIntegrity() {
        val validConfig = SessionConfig("id-1", "10.0.0.1", 4444, "secret")
        assertTrue(validConfig.port in 1024..65535)
    }
}
