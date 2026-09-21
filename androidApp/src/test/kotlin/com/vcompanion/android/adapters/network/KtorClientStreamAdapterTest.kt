package com.vcompanion.android.adapters.network

import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.protocol.CoreJson
import com.vcompanion.shared.core.protocol.ProtocolMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorClientStreamAdapterTest {

    @Test
    fun shouldRespondWithPongImmediatelyWhenPingReceived() = runTest {
        val adapter = KtorClientStreamAdapter()
        var repliedPayload: String? = null

        val pingMsg = ProtocolMessage.Ping(clientTimestamp = 1690000000L)
        val pingJson = CoreJson.encodeToString(ProtocolMessage.serializer(), pingMsg)

        adapter.handleIncomingControlText(pingJson) { reply ->
            repliedPayload = reply
        }

        assertTrue("Should have replied with pong", repliedPayload != null)
        val decodedReply = CoreJson.decodeFromString(ProtocolMessage.serializer(), repliedPayload!!)
        assertTrue(decodedReply is ProtocolMessage.Pong)
        assertEquals(1690000000L, (decodedReply as ProtocolMessage.Pong).clientTimestamp)
    }

    @Test
    fun shouldEmitCommandMessageOnIncomingMessagesFlow() = runTest {
        val adapter = KtorClientStreamAdapter()

        val commandMsg = ProtocolMessage.CommandPacket(command = CameraCommand.ToggleTorch)
        val commandJson = CoreJson.encodeToString(ProtocolMessage.serializer(), commandMsg)

        adapter.handleIncomingControlText(commandJson) { }

        val received = adapter.incomingMessages.first()
        assertTrue(received is ProtocolMessage.CommandPacket)
        assertEquals(CameraCommand.ToggleTorch, (received as ProtocolMessage.CommandPacket).command)
    }

    @Test
    fun shouldIgnoreMalformedIncomingTextWithoutCrashing() = runTest {
        val adapter = KtorClientStreamAdapter()
        var replied = false

        adapter.handleIncomingControlText("MALFORMED_JSON_STRING") {
            replied = true
        }

        assertEquals(false, replied)
    }
}
