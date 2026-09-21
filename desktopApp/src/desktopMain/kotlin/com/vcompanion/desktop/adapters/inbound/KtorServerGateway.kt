package com.vcompanion.desktop.adapters.inbound

import com.vcompanion.shared.core.ports.IStreamGateway
import com.vcompanion.shared.core.protocol.CoreJson
import com.vcompanion.shared.core.protocol.ProtocolMessage
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Servidor Ktor embebido para ingesta y señalización de Virtual Studio Companion.
 * Implementa [IStreamGateway] con fallback de puertos (8080-8090) y medición RTT periódica (RF-001, RF-004, RNF-002).
 */
class KtorServerGateway(
    private val pingIntervalMs: Long = 1000L
) : IStreamGateway {

    private var server: ApplicationEngine? = null
    var effectivePort: Int = 0
        private set

    private val _incomingMessages = MutableSharedFlow<ProtocolMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<ProtocolMessage> = _incomingMessages.asSharedFlow()

    private val _rttLatencyMs = MutableStateFlow(-1L)
    val rttLatencyMs: StateFlow<Long> = _rttLatencyMs.asStateFlow()

    private val _connectedClientsCount = MutableStateFlow(0)
    val connectedClientsCount: StateFlow<Int> = _connectedClientsCount.asStateFlow()

    private val activeSessions = CopyOnWriteArrayList<WebSocketSession>()
    private val serverScope = CoroutineScope(Dispatchers.Default + Job())

    suspend fun start(startPort: Int = 8080, maxPort: Int = 8090): Int {
        for (port in startPort..maxPort) {
            if (!isPortAvailable(port)) continue

            try {
                val engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                    install(WebSockets) {
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }
                    routing {
                        webSocket("/ws/control") {
                            handleControlSession(this)
                        }
                        webSocket("/ws/stream") {
                            handleStreamSession(this)
                        }
                        get("/stream/preview") {
                            call.respondText(
                                "<!DOCTYPE html><html><head><title>Virtual Studio Camera Preview</title></head>" +
                                    "<body style=\"background:#000;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;\">" +
                                    "<h1>Virtual Studio Camera Preview</h1></body></html>",
                                ContentType.Text.Html
                            )
                        }
                    }
                }
                engine.start(wait = false)
                server = engine
                effectivePort = port
                return port
            } catch (_: Exception) {
                // Try next port in range
            }
        }
        error("Could not bind Ktor server on any port in range $startPort..$maxPort")
    }

    private suspend fun handleControlSession(session: WebSocketSession) {
        activeSessions.add(session)
        _connectedClientsCount.value = activeSessions.size

        val pingJob = serverScope.launch {
            while (isActive) {
                delay(pingIntervalMs)
                try {
                    val ping = ProtocolMessage.Ping(clientTimestamp = System.currentTimeMillis())
                    val payload = CoreJson.encodeToString(ProtocolMessage.serializer(), ping)
                    session.send(Frame.Text(payload))
                } catch (_: Exception) {
                    break
                }
            }
        }

        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    try {
                        val text = frame.readText()
                        val msg = CoreJson.decodeFromString(ProtocolMessage.serializer(), text)
                        if (msg is ProtocolMessage.Pong) {
                            val rtt = System.currentTimeMillis() - msg.clientTimestamp
                            _rttLatencyMs.value = if (rtt >= 0) rtt else 0L
                        }
                        _incomingMessages.emit(msg)
                    } catch (_: Exception) {
                        // Ignore malformed messages
                    }
                }
            }
        } catch (_: Exception) {
            // Socket error or connection lost handled gracefully
        } finally {
            pingJob.cancel()
            activeSessions.remove(session)
            _connectedClientsCount.value = activeSessions.size
            _incomingMessages.emit(ProtocolMessage.DisconnectRequest("CLIENT_CLOSED"))
        }
    }

    private suspend fun handleStreamSession(session: WebSocketSession) {
        activeSessions.add(session)
        _connectedClientsCount.value = activeSessions.size
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    try {
                        val text = frame.readText()
                        val msg = CoreJson.decodeFromString(ProtocolMessage.serializer(), text)
                        _incomingMessages.emit(msg)
                    } catch (_: Exception) {
                        // Ignore malformed messages
                    }
                }
            }
        } finally {
            activeSessions.remove(session)
            _connectedClientsCount.value = activeSessions.size
        }
    }

    override suspend fun sendMessage(message: ProtocolMessage): Result<Unit> {
        return runCatching {
            val payload = CoreJson.encodeToString(ProtocolMessage.serializer(), message)
            for (session in activeSessions) {
                session.send(Frame.Text(payload))
            }
        }
    }

    override suspend fun disconnect() {
        try {
            val disconnectMsg = ProtocolMessage.DisconnectRequest("HOST_SHUTDOWN")
            val payload = CoreJson.encodeToString(ProtocolMessage.serializer(), disconnectMsg)
            for (session in activeSessions) {
                runCatching { session.send(Frame.Text(payload)) }
                runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "Server shutdown")) }
            }
        } catch (_: Exception) {
            // Ignore during shutdown
        }
        activeSessions.clear()
        _connectedClientsCount.value = 0
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1500)
        server = null
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: Exception) {
            false
        }
    }
}
