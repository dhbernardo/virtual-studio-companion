package com.vcompanion.android.adapters.network

import android.os.Build
import com.vcompanion.shared.core.ports.IStreamGateway
import com.vcompanion.shared.core.protocol.CoreJson
import com.vcompanion.shared.core.protocol.ProtocolMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KtorClientStreamAdapter(
    private val client: HttpClient = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 15_000
        }
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IStreamGateway {

    private val adapterScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _incomingMessages = MutableSharedFlow<ProtocolMessage>(replay = 1, extraBufferCapacity = 64)
    override val incomingMessages: Flow<ProtocolMessage> = _incomingMessages.asSharedFlow()

    private var controlSession: DefaultClientWebSocketSession? = null
    private var streamSession: DefaultClientWebSocketSession? = null
    private var controlJob: Job? = null
    private var streamJob: Job? = null

    suspend fun handleIncomingControlText(
        text: String,
        sendReply: suspend (String) -> Unit
    ) {
        try {
            val message = CoreJson.decodeFromString(ProtocolMessage.serializer(), text)
            if (message is ProtocolMessage.Ping) {
                val pong = ProtocolMessage.Pong(clientTimestamp = message.clientTimestamp)
                val pongJson = CoreJson.encodeToString(ProtocolMessage.serializer(), pong)
                sendReply(pongJson)
            } else {
                _incomingMessages.emit(message)
            }
        } catch (_: Exception) {
            // Ignore malformed payloads
        }
    }

    suspend fun connect(
        host: String,
        port: Int,
        sessionToken: String,
        clientVersion: String = "1.0.0",
        deviceModel: String = try { Build.MODEL ?: "Android Device" } catch (_: Throwable) { "Android Device" }
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            disconnect()

            val controlReadyJob = Job()
            controlJob = adapterScope.launch {
                try {
                    client.webSocket(host = host, port = port, path = "/ws/control") {
                        controlSession = this
                        controlReadyJob.complete()

                        val initMsg = ProtocolMessage.HandshakeInit(
                            clientVersion = clientVersion,
                            deviceModel = deviceModel,
                            sessionToken = sessionToken
                        )
                        val initJson = CoreJson.encodeToString(ProtocolMessage.serializer(), initMsg)
                        send(Frame.Text(initJson))

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleIncomingControlText(text) { reply ->
                                    send(Frame.Text(reply))
                                }
                            }
                        }
                        _incomingMessages.emit(ProtocolMessage.DisconnectRequest(reason = "SERVER_CLOSED"))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _incomingMessages.emit(ProtocolMessage.DisconnectRequest(reason = e.message ?: "Connection error"))
                } finally {
                    controlSession = null
                    if (!controlReadyJob.isCompleted) controlReadyJob.complete()
                }
            }

            streamJob = adapterScope.launch {
                try {
                    client.webSocket(host = host, port = port, path = "/ws/stream") {
                        streamSession = this
                        for (frame in incoming) {
                            // Stream WebSocket is mainly for outbound binary frames
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Safe handling of stream connection errors
                } finally {
                    streamSession = null
                }
            }

            controlReadyJob.join()
        }
    }

    suspend fun sendVideoFrame(frameBytes: ByteArray): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val session = streamSession ?: error("Stream session is not connected")
            session.send(Frame.Binary(true, frameBytes))
        }
    }

    override suspend fun sendMessage(message: ProtocolMessage): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val session = controlSession ?: error("Control session is not connected")
            val payload = CoreJson.encodeToString(ProtocolMessage.serializer(), message)
            session.send(Frame.Text(payload))
        }
    }

    override suspend fun disconnect(): Unit = withContext(ioDispatcher) {
        try {
            val disconnectMsg = ProtocolMessage.DisconnectRequest("USER_REQUEST")
            val payload = CoreJson.encodeToString(ProtocolMessage.serializer(), disconnectMsg)
            controlSession?.runCatching { send(Frame.Text(payload)) }
            controlSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
            streamSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
        } catch (_: Exception) {
            // Ignore on disconnect
        } finally {
            controlJob?.cancel()
            streamJob?.cancel()
            controlSession = null
            streamSession = null
        }
        Unit
    }
}
