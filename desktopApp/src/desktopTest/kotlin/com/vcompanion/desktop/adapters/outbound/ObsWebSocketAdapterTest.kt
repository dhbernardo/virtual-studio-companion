package com.vcompanion.desktop.adapters.outbound

import com.vcompanion.shared.core.ports.ObsConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeObsTransport : IObsTransport {
    private val _incoming = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
    override val incoming: Flow<String> = _incoming.asSharedFlow()

    val sentMessages = mutableListOf<String>()
    var isConnected = false
    var onMessageSent: ((String) -> Unit)? = null

    override suspend fun connect(host: String, port: Int): Result<Unit> {
        isConnected = true
        return Result.success(Unit)
    }

    override suspend fun send(text: String): Result<Unit> {
        sentMessages.add(text)
        onMessageSent?.invoke(text)
        return Result.success(Unit)
    }

    suspend fun emitIncoming(text: String) {
        _incoming.emit(text)
    }

    override suspend fun disconnect() {
        isConnected = false
    }
}

class ObsWebSocketAdapterTest {

    private var adapter: ObsWebSocketAdapter? = null

    @AfterTest
    fun tearDown() {
        runBlocking {
            adapter?.disconnect()
            adapter?.close()
        }
    }

    @Test
    fun shouldComputeSha256AuthChallengeCorrectly() {
        val password = "mySuperSecretPassword"
        val salt = "dGVzdFNhbHQ="
        val challenge = "dGVzdENoYWxsZW5nZQ=="

        val md = MessageDigest.getInstance("SHA-256")
        val secretHash = md.digest((password + salt).toByteArray(Charsets.UTF_8))
        val secretBase64 = Base64.getEncoder().encodeToString(secretHash)
        val authHash = md.digest((secretBase64 + challenge).toByteArray(Charsets.UTF_8))
        val expectedAuth = Base64.getEncoder().encodeToString(authHash)

        val actualAuth = ObsWebSocketAdapter.computeAuthResponse(password, salt, challenge)
        assertEquals(expectedAuth, actualAuth)
    }

    @Test
    fun shouldAuthenticateWithObsAndReachConnectedState() = runBlocking {
        val fakeTransport = FakeObsTransport()
        val expectedPassword = "testPassword123"
        val testSalt = "salt12345"
        val testChallenge = "challenge67890"
        val expectedAuth = ObsWebSocketAdapter.computeAuthResponse(expectedPassword, testSalt, testChallenge)

        var identifyReceived = false

        fakeTransport.onMessageSent = { text ->
            val json = Json.parseToJsonElement(text).jsonObject
            val op = json["op"]?.jsonPrimitive?.int
            if (op == 1) { // Identify
                val auth = json["d"]?.jsonObject?.get("authentication")?.jsonPrimitive?.content
                if (auth == expectedAuth) {
                    identifyReceived = true
                    // Server responds with Identified (op: 2)
                    runBlocking {
                        val identified = buildJsonObject {
                            put("op", 2)
                            putJsonObject("d") {
                                put("negotiatedRpcVersion", 1)
                            }
                        }.toString()
                        fakeTransport.emitIncoming(identified)
                    }
                }
            }
        }

        adapter = ObsWebSocketAdapter(transport = fakeTransport)

        // Launch connect asynchronously
        val connectJob = async {
            adapter!!.connect(host = "127.0.0.1", port = 4455, password = expectedPassword)
        }

        // Server sends Hello (op: 0)
        val hello = buildJsonObject {
            put("op", 0)
            putJsonObject("d") {
                put("obsWebSocketVersion", "5.1.0")
                put("rpcVersion", 1)
                putJsonObject("authentication") {
                    put("challenge", testChallenge)
                    put("salt", testSalt)
                }
            }
        }.toString()
        fakeTransport.emitIncoming(hello)

        val connectResult = connectJob.await()
        assertTrue(connectResult.isSuccess, "Connect should succeed")
        assertTrue(identifyReceived, "Identify should contain expected SHA256 auth response")
        assertEquals(ObsConnectionState.CONNECTED, adapter!!.connectionState.value)
    }

    @Test
    fun shouldSetupBrowserSourceIdempotently() = runBlocking {
        val fakeTransport = FakeObsTransport()
        var createInputCalled = false
        var setInputSettingsCalled = false

        fakeTransport.onMessageSent = { text ->
            val json = Json.parseToJsonElement(text).jsonObject
            val op = json["op"]?.jsonPrimitive?.int
            if (op == 1) { // Identify
                runBlocking {
                    val identified = buildJsonObject {
                        put("op", 2)
                        putJsonObject("d") { put("negotiatedRpcVersion", 1) }
                    }.toString()
                    fakeTransport.emitIncoming(identified)
                }
            } else if (op == 6) { // Request
                val d = json["d"]?.jsonObject
                val reqType = d?.get("requestType")?.jsonPrimitive?.content
                val reqId = d?.get("requestId")?.jsonPrimitive?.content ?: "1"

                when (reqType) {
                    "GetInputSettings" -> {
                        runBlocking {
                            val resp = buildJsonObject {
                                put("op", 7)
                                putJsonObject("d") {
                                    put("requestType", "GetInputSettings")
                                    put("requestId", reqId)
                                    putJsonObject("requestStatus") {
                                        put("result", false)
                                        put("code", 600)
                                    }
                                }
                            }.toString()
                            fakeTransport.emitIncoming(resp)
                        }
                    }
                    "GetCurrentProgramScene" -> {
                        runBlocking {
                            val resp = buildJsonObject {
                                put("op", 7)
                                putJsonObject("d") {
                                    put("requestType", "GetCurrentProgramScene")
                                    put("requestId", reqId)
                                    putJsonObject("requestStatus") {
                                        put("result", true)
                                        put("code", 100)
                                    }
                                    putJsonObject("responseData") {
                                        put("currentProgramSceneName", "Main Scene")
                                    }
                                }
                            }.toString()
                            fakeTransport.emitIncoming(resp)
                        }
                    }
                    "CreateInput" -> {
                        createInputCalled = true
                        runBlocking {
                            val resp = buildJsonObject {
                                put("op", 7)
                                putJsonObject("d") {
                                    put("requestType", "CreateInput")
                                    put("requestId", reqId)
                                    putJsonObject("requestStatus") {
                                        put("result", true)
                                        put("code", 100)
                                    }
                                }
                            }.toString()
                            fakeTransport.emitIncoming(resp)
                        }
                    }
                    "SetInputSettings" -> {
                        setInputSettingsCalled = true
                        runBlocking {
                            val resp = buildJsonObject {
                                put("op", 7)
                                putJsonObject("d") {
                                    put("requestType", "SetInputSettings")
                                    put("requestId", reqId)
                                    putJsonObject("requestStatus") {
                                        put("result", true)
                                        put("code", 100)
                                    }
                                }
                            }.toString()
                            fakeTransport.emitIncoming(resp)
                        }
                    }
                }
            }
        }

        adapter = ObsWebSocketAdapter(transport = fakeTransport)

        // Connect with Hello/Identified
        val connectJob = async {
            adapter!!.connect(host = "127.0.0.1", port = 4455, password = null)
        }
        val hello = buildJsonObject {
            put("op", 0)
            putJsonObject("d") {
                put("obsWebSocketVersion", "5.1.0")
                put("rpcVersion", 1)
            }
        }.toString()
        fakeTransport.emitIncoming(hello)
        connectJob.await()

        val result = adapter!!.setupBrowserSource(
            sourceName = "Virtual Studio Camera",
            previewUrl = "http://localhost:8080/stream/preview",
            width = 1920,
            height = 1080,
            fps = 60
        )

        assertTrue(result.isSuccess, "Browser source setup should succeed")
        assertTrue(createInputCalled, "CreateInput should be invoked when source does not exist")
    }

    @Test
    fun shouldUpdateExistingBrowserSourceIdempotently() = runBlocking {
        val fakeTransport = FakeObsTransport()
        var setInputSettingsCalled = false

        fakeTransport.onMessageSent = { text ->
            val json = Json.parseToJsonElement(text).jsonObject
            val op = json["op"]?.jsonPrimitive?.int
            if (op == 1) { // Identify
                runBlocking {
                    val identified = buildJsonObject {
                        put("op", 2)
                        putJsonObject("d") { put("negotiatedRpcVersion", 1) }
                    }.toString()
                    fakeTransport.emitIncoming(identified)
                }
            } else if (op == 6) { // Request
                val d = json["d"]?.jsonObject
                val reqType = d?.get("requestType")?.jsonPrimitive?.content
                val reqId = d?.get("requestId")?.jsonPrimitive?.content ?: "1"

                when (reqType) {
                    "GetInputSettings" -> {
                        runBlocking {
                            // Source already exists!
                            val resp = buildJsonObject {
                                put("op", 7)
                                putJsonObject("d") {
                                    put("requestType", "GetInputSettings")
                                    put("requestId", reqId)
                                    putJsonObject("requestStatus") {
                                        put("result", true)
                                        put("code", 100)
                                    }
                                }
                            }.toString()
                            fakeTransport.emitIncoming(resp)
                        }
                    }
                    "SetInputSettings" -> {
                        setInputSettingsCalled = true
                        runBlocking {
                            val resp = buildJsonObject {
                                put("op", 7)
                                putJsonObject("d") {
                                    put("requestType", "SetInputSettings")
                                    put("requestId", reqId)
                                    putJsonObject("requestStatus") {
                                        put("result", true)
                                        put("code", 100)
                                    }
                                }
                            }.toString()
                            fakeTransport.emitIncoming(resp)
                        }
                    }
                }
            }
        }

        adapter = ObsWebSocketAdapter(transport = fakeTransport)

        val connectJob = async {
            adapter!!.connect(host = "127.0.0.1", port = 4455, password = null)
        }
        val hello = buildJsonObject {
            put("op", 0)
            putJsonObject("d") {
                put("obsWebSocketVersion", "5.1.0")
                put("rpcVersion", 1)
            }
        }.toString()
        fakeTransport.emitIncoming(hello)
        connectJob.await()

        val result = adapter!!.setupBrowserSource(
            sourceName = "Virtual Studio Camera",
            previewUrl = "http://localhost:8080/stream/preview",
            width = 1920,
            height = 1080,
            fps = 60
        )

        assertTrue(result.isSuccess, "Browser source setup should succeed")
        assertTrue(setInputSettingsCalled, "SetInputSettings should be invoked when source already exists")
    }
}
