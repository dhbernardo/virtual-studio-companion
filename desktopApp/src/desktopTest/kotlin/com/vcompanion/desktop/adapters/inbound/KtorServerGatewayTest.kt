package com.vcompanion.desktop.adapters.inbound

import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.protocol.CoreJson
import com.vcompanion.shared.core.protocol.ProtocolMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KtorServerGatewayTest {

    private var gateway: KtorServerGateway? = null
    private var occupiedSocket: ServerSocket? = null

    @AfterTest
    fun tearDown() {
        runBlocking {
            gateway?.disconnect()
            occupiedSocket?.close()
        }
    }

    @Test
    fun shouldFallbackToNextPortWhenDefaultPortIsOccupied() = runBlocking {
        // Occupy port 8080
        val testPort = 8080
        occupiedSocket = ServerSocket(testPort)

        gateway = KtorServerGateway(pingIntervalMs = 1000)
        val boundPort = gateway!!.start(startPort = testPort, maxPort = 8090)

        assertEquals(8081, boundPort, "Should have bound to next available port 8081")
        assertEquals(8081, gateway!!.effectivePort)
    }

    @Test
    fun shouldDeserializeCommandPacketFromWebSocket() = runBlocking {
        gateway = KtorServerGateway(pingIntervalMs = 1000)
        val port = gateway!!.start(startPort = 8082, maxPort = 8090)

        val client = HttpClient {
            install(WebSockets)
        }

        val receivedMessages = mutableListOf<ProtocolMessage>()
        val job = launch {
            gateway!!.incomingMessages.collect { msg ->
                receivedMessages.add(msg)
            }
        }

        try {
            client.webSocket(host = "127.0.0.1", port = port, path = "/ws/control") {
                val commandPacket: ProtocolMessage = ProtocolMessage.CommandPacket(
                    command = CameraCommand.ToggleTorch
                )
                val json = CoreJson.encodeToString(ProtocolMessage.serializer(), commandPacket)
                send(Frame.Text(json))

                // Wait briefly for server to process incoming message
                withTimeout(2000) {
                    while (receivedMessages.isEmpty()) {
                        kotlinx.coroutines.delay(50)
                    }
                }
            }

            assertTrue(receivedMessages.isNotEmpty())
            val cmd = receivedMessages.first() as? ProtocolMessage.CommandPacket
            assertNotNull(cmd)
            assertEquals(CameraCommand.ToggleTorch, cmd.command)
        } finally {
            client.close()
            job.cancel()
        }
    }

    @Test
    fun shouldEmitPeriodicPingAndCalculateRttOnPong() = runBlocking {
        gateway = KtorServerGateway(pingIntervalMs = 200)
        val port = gateway!!.start(startPort = 8083, maxPort = 8090)

        val client = HttpClient {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = port, path = "/ws/control") {
                // Client receives Ping from server and replies with Pong
                var pingReceived: ProtocolMessage.Ping? = null
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val msg = CoreJson.decodeFromString(ProtocolMessage.serializer(), frame.readText())
                        if (msg is ProtocolMessage.Ping) {
                            pingReceived = msg
                            val pong = ProtocolMessage.Pong(clientTimestamp = msg.clientTimestamp)
                            send(Frame.Text(CoreJson.encodeToString(ProtocolMessage.serializer(), pong)))
                            break
                        }
                    }
                }

                assertNotNull(pingReceived)
                // Wait for gateway to update RTT latency
                withTimeout(2000) {
                    while (gateway!!.rttLatencyMs.value < 0) {
                        kotlinx.coroutines.delay(20)
                    }
                }
                assertTrue(gateway!!.rttLatencyMs.value >= 0, "RTT latency should be calculated")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun shouldBroadcastMessageToAllConnectedClients() = runBlocking {
        gateway = KtorServerGateway(pingIntervalMs = 5000)
        val port = gateway!!.start(startPort = 8084, maxPort = 8090)

        val client = HttpClient {
            install(WebSockets)
        }

        val client1Messages = mutableListOf<ProtocolMessage>()
        val client2Messages = mutableListOf<ProtocolMessage>()

        try {
            client.webSocket(host = "127.0.0.1", port = port, path = "/ws/control") {
                val session1 = this
                client.webSocket(host = "127.0.0.1", port = port, path = "/ws/control") {
                    val session2 = this

                    val job1 = launch {
                        for (frame in session1.incoming) {
                            if (frame is Frame.Text) {
                                val msg = CoreJson.decodeFromString(ProtocolMessage.serializer(), frame.readText())
                                if (msg !is ProtocolMessage.Ping) client1Messages.add(msg)
                            }
                        }
                    }

                    val job2 = launch {
                        for (frame in session2.incoming) {
                            if (frame is Frame.Text) {
                                val msg = CoreJson.decodeFromString(ProtocolMessage.serializer(), frame.readText())
                                if (msg !is ProtocolMessage.Ping) client2Messages.add(msg)
                            }
                        }
                    }

                    // Wait for both sessions to register
                    withTimeout(2000) {
                        while (gateway!!.connectedClientsCount.value < 2) {
                            kotlinx.coroutines.delay(50)
                        }
                    }
                    assertEquals(2, gateway!!.connectedClientsCount.value)

                    val ack = ProtocolMessage.HandshakeAck(serverVersion = "1.0.0", approvedFps = 60, approvedResolution = "1080p")
                    gateway!!.sendMessage(ack)

                    withTimeout(2000) {
                        while (client1Messages.isEmpty() || client2Messages.isEmpty()) {
                            kotlinx.coroutines.delay(50)
                        }
                    }

                    assertEquals(ack, client1Messages.first())
                    assertEquals(ack, client2Messages.first())

                    job1.cancel()
                    job2.cancel()
                }
            }
        } finally {
            client.close()
        }
    }
}
