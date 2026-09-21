package com.vcompanion.desktop.adapters.outbound

import com.vcompanion.shared.core.ports.IObsConnector
import com.vcompanion.shared.core.ports.ObsConnectionState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Contrato de transporte para comunicación WebSocket con OBS Studio.
 */
interface IObsTransport {
    suspend fun connect(host: String, port: Int): Result<Unit>
    suspend fun send(text: String): Result<Unit>
    val incoming: Flow<String>
    suspend fun disconnect()
}

/**
 * Implementación de producción de [IObsTransport] utilizando Ktor Client WebSocket.
 */
class KtorObsTransport(
    private val client: HttpClient = HttpClient { install(WebSockets) }
) : IObsTransport {
    private var session: DefaultClientWebSocketSession? = null
    private val _incoming = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
    override val incoming: Flow<String> = _incoming.asSharedFlow()
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var readJob: Job? = null

    override suspend fun connect(host: String, port: Int): Result<Unit> {
        return runCatching {
            disconnect()
            val wsSession = client.webSocketSession(host = host, port = port, path = "/")
            session = wsSession
            readJob?.cancel()
            readJob = scope.launch {
                try {
                    for (frame in wsSession.incoming) {
                        if (frame is Frame.Text) {
                            _incoming.emit(frame.readText())
                        }
                    }
                } catch (_: Exception) {
                    // Channel closed
                }
            }
        }
    }

    override suspend fun send(text: String): Result<Unit> {
        return runCatching {
            session?.send(Frame.Text(text)) ?: error("Session not connected")
        }
    }

    override suspend fun disconnect() {
        readJob?.cancel()
        runCatching {
            withTimeoutOrNull(300) {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnect"))
            }
        }
        session = null
    }

    fun close() {
        scope.coroutineContext[Job]?.cancelChildren()
        client.close()
    }
}

/**
 * Adaptador de salida para OBS Studio WebSocket v5.
 * Implementa [IObsConnector], soporta autenticación SHA256, configuración idempotente de Browser Source
 * y reconexión con retroceso exponencial (RF-003, RNF-003).
 */
class ObsWebSocketAdapter(
    private val transport: IObsTransport = KtorObsTransport(),
    private val backoffDelaysMs: List<Long> = listOf(2000L, 4000L, 8000L)
) : IObsConnector {

    private val _connectionState = MutableStateFlow(ObsConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ObsConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var reconnectJob: Job? = null
    private var transportJob: Job? = null

    private val connectMutex = Mutex()
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val identifiedDeferred = CompletableDeferred<Unit>()

    private var lastHost: String = "localhost"
    private var lastPort: Int = 4455
    private var lastPassword: String? = null
    private var intentionalDisconnect = false

    override suspend fun connect(host: String, port: Int, password: String?): Result<Unit> {
        lastHost = host
        lastPort = port
        lastPassword = password
        intentionalDisconnect = false

        return doConnect(host, port, password)
    }

    private suspend fun doConnect(host: String, port: Int, password: String?): Result<Unit> = connectMutex.withLock {
        _connectionState.value = ObsConnectionState.CONNECTING

        val handshakeDeferred = CompletableDeferred<Unit>()

        // 1. Cancel previous transport job without triggering reconnect in finally
        val previousJob = transportJob
        transportJob = null
        previousJob?.cancel()

        // 2. Subscribe before connecting transport to ensure op:0 (Hello) is captured deterministically
        val currentJob = scope.launch {
            try {
                transport.incoming.collect { text ->
                    handleIncomingMessage(text, password, handshakeDeferred)
                }
            } finally {
                if (!intentionalDisconnect && transportJob != null) {
                    _connectionState.value = ObsConnectionState.DISCONNECTED
                    scheduleReconnect()
                }
            }
        }
        transportJob = currentJob

        val connectResult = transport.connect(host, port)
        if (connectResult.isFailure) {
            _connectionState.value = ObsConnectionState.ERROR
            scheduleReconnect()
            return connectResult
        }

        return runCatching {
            withTimeout(4000) {
                handshakeDeferred.await()
            }
            _connectionState.value = ObsConnectionState.CONNECTED
        }.onFailure {
            _connectionState.value = ObsConnectionState.ERROR
            scheduleReconnect()
        }
    }

    private suspend fun handleIncomingMessage(
        text: String,
        password: String?,
        handshakeDeferred: CompletableDeferred<Unit>
    ) {
        try {
            val json = Json.parseToJsonElement(text).jsonObject
            val op = json["op"]?.jsonPrimitive?.int ?: return
            val d = json["d"]?.jsonObject

            when (op) {
                0 -> { // Hello from server
                    val authObj = d?.get("authentication")?.jsonObject
                    val identifyPayload = buildJsonObject {
                        put("op", 1) // Identify
                        putJsonObject("d") {
                            put("rpcVersion", 1)
                            if (authObj != null && password != null) {
                                val challenge = authObj["challenge"]?.jsonPrimitive?.content ?: ""
                                val salt = authObj["salt"]?.jsonPrimitive?.content ?: ""
                                val authResponse = computeAuthResponse(password, salt, challenge)
                                put("authentication", authResponse)
                            }
                            put("eventSubscriptions", 0)
                        }
                    }.toString()
                    transport.send(identifyPayload)
                }
                2 -> { // Identified from server
                    handshakeDeferred.complete(Unit)
                }
                7 -> { // RequestResponse
                    val requestId = d?.get("requestId")?.jsonPrimitive?.content
                    if (requestId != null) {
                        pendingRequests.remove(requestId)?.complete(d)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore malformed packet
        }
    }

    private fun scheduleReconnect() {
        if (intentionalDisconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (delayMs in backoffDelaysMs) {
                if (!isActive || intentionalDisconnect) break
                delay(delayMs)
                val res = doConnect(lastHost, lastPort, lastPassword)
                if (res.isSuccess) {
                    break
                }
            }
        }
    }

    suspend fun sendRequest(requestType: String, requestData: JsonObject = JsonObject(emptyMap())): Result<JsonObject> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[requestId] = deferred

        return runCatching {
            val reqPayload = buildJsonObject {
                put("op", 6) // Request
                putJsonObject("d") {
                    put("requestType", requestType)
                    put("requestId", requestId)
                    put("requestData", requestData)
                }
            }.toString()

            val sendRes = transport.send(reqPayload)
            if (sendRes.isFailure) {
                throw sendRes.exceptionOrNull() ?: IllegalStateException("Failed to send request")
            }

            val response = withTimeout(4000) {
                deferred.await()
            }

            val status = response["requestStatus"]?.jsonObject
            val isSuccess = status?.get("result")?.jsonPrimitive?.boolean ?: false
            if (!isSuccess) {
                val code = status?.get("code")?.jsonPrimitive?.int ?: -1
                val comment = status?.get("comment")?.jsonPrimitive?.content ?: "Request failed"
                error("OBS request error $code: $comment")
            }
            response["responseData"]?.jsonObject ?: JsonObject(emptyMap())
        }.also {
            pendingRequests.remove(requestId)
        }
    }

    override suspend fun setupBrowserSource(
        sourceName: String,
        previewUrl: String,
        width: Int,
        height: Int,
        fps: Int
    ): Result<Unit> {
        return runCatching {
            val checkInput = sendRequest(
                "GetInputSettings",
                buildJsonObject { put("inputName", sourceName) }
            )

            val inputSettingsObj = buildJsonObject {
                put("url", previewUrl)
                put("width", width)
                put("height", height)
                put("fps", fps)
            }

            if (checkInput.isSuccess) {
                sendRequest(
                    "SetInputSettings",
                    buildJsonObject {
                        put("inputName", sourceName)
                        put("inputSettings", inputSettingsObj)
                    }
                ).getOrThrow()
            } else {
                val sceneResp = sendRequest("GetCurrentProgramScene").getOrNull()
                val sceneName = sceneResp?.get("currentProgramSceneName")?.jsonPrimitive?.content ?: ""

                sendRequest(
                    "CreateInput",
                    buildJsonObject {
                        if (sceneName.isNotBlank()) put("sceneName", sceneName)
                        put("inputName", sourceName)
                        put("inputKind", "browser_source")
                        put("inputSettings", inputSettingsObj)
                    }
                ).getOrThrow()
            }
            Unit
        }
    }

    override suspend fun disconnect() = connectMutex.withLock {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        val job = transportJob
        transportJob = null
        job?.cancel()
        pendingRequests.values.forEach { deferred -> deferred.cancel() }
        pendingRequests.clear()
        runCatching {
            transport.disconnect()
        }
        _connectionState.value = ObsConnectionState.DISCONNECTED
    }

    fun close() {
        scope.coroutineContext[Job]?.cancelChildren()
        (transport as? KtorObsTransport)?.close()
    }

    companion object {
        fun computeAuthResponse(password: String, salt: String, challenge: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val secretHash = md.digest((password + salt).toByteArray(Charsets.UTF_8))
            val secretBase64 = Base64.getEncoder().encodeToString(secretHash)

            val authHash = md.digest((secretBase64 + challenge).toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(authHash)
        }
    }
}
