package com.vcompanion.desktop.adapters.inbound

import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.protocol.CoreJson
import com.vcompanion.shared.core.protocol.ProtocolMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
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
        var targetPort = 8080
        var socket: ServerSocket? = null
        for (port in 8080..8085) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(java.net.InetSocketAddress("127.0.0.1", port))
                socket = s
                targetPort = port
                break
            } catch (_: Exception) {
                // Port occupied, try next
            }
        }
        occupiedSocket = socket

        gateway = KtorServerGateway(pingIntervalMs = 1000)
        val boundPort = gateway!!.start(startPort = targetPort, maxPort = targetPort + 5)

        assertEquals(targetPort + 1, boundPort, "Should have bound to next available port")
        assertEquals(targetPort + 1, gateway!!.effectivePort)
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
        val port = gateway!!.start(startPort = 8130, maxPort = 8140)

        val client1 = HttpClient { install(WebSockets) }
        val client2 = HttpClient { install(WebSockets) }

        val client1Messages = java.util.concurrent.CopyOnWriteArrayList<ProtocolMessage>()
        val client2Messages = java.util.concurrent.CopyOnWriteArrayList<ProtocolMessage>()

        suspend fun connectWithRetry(client: HttpClient, port: Int, messageList: java.util.concurrent.CopyOnWriteArrayList<ProtocolMessage>) {
            var connected = false
            for (attempt in 1..5) {
                try {
                    client.webSocket(host = "127.0.0.1", port = port, path = "/ws/control") {
                        connected = true
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val msg = CoreJson.decodeFromString(ProtocolMessage.serializer(), frame.readText())
                                if (msg !is ProtocolMessage.Ping) {
                                    messageList.add(msg)
                                }
                            }
                        }
                    }
                    break
                } catch (_: Exception) {
                    if (connected) break
                    kotlinx.coroutines.delay(100)
                }
            }
        }

        val job1 = launch(Dispatchers.IO) {
            connectWithRetry(client1, port, client1Messages)
        }

        val job2 = launch(Dispatchers.IO) {
            connectWithRetry(client2, port, client2Messages)
        }

        try {
            // Wait for both sessions to register
            withTimeout(8000) {
                while (gateway!!.connectedClientsCount.value < 2) {
                    kotlinx.coroutines.delay(50)
                }
            }
            assertEquals(2, gateway!!.connectedClientsCount.value)

            val ack = ProtocolMessage.HandshakeAck(serverVersion = "1.0.0", approvedFps = 60, approvedResolution = "1080p")
            gateway!!.sendMessage(ack)

            withTimeout(8000) {
                while (client1Messages.isEmpty() || client2Messages.isEmpty()) {
                    kotlinx.coroutines.delay(50)
                }
            }

            assertEquals(ack, client1Messages.first())
            assertEquals(ack, client2Messages.first())
        } finally {
            job1.cancel()
            job2.cancel()
            client1.close()
            client2.close()
        }
    }
}
