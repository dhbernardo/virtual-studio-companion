package com.vcompanion.shared.core.domain

import app.cash.turbine.test
import com.vcompanion.shared.core.domain.model.ConnectionError
import com.vcompanion.shared.core.domain.model.ConnectionErrorCode
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.domain.model.ConnectionStateMachine
import com.vcompanion.shared.core.domain.model.PairingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * T03: Pruebas unitarias para la máquina de estados con Turbine.
 * Valida RF-002 y RNF-001.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionStateMachineTest {

    private val sampleConfig = PairingConfig(
        host = "192.168.1.100",
        port = 8080,
        sessionToken = "tok_test_123"
    )

    @Test
    fun shouldStartInDisconnectedState() = runTest {
        val fsm = ConnectionStateMachine(scope = this)

        fsm.state.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shouldFollowHappyPathTransitions() = runTest {
        val fsm = ConnectionStateMachine(scope = this)

        fsm.state.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())

            fsm.onScanQr(sampleConfig)
            val pairingState = awaitItem()
            assertIs<ConnectionState.Pairing>(pairingState)
            assertEquals(sampleConfig, pairingState.config)

            fsm.onHandshakeSuccess(sessionInfo = "session-ok")
            val connectedState = awaitItem()
            assertIs<ConnectionState.Connected>(connectedState)
            assertEquals("session-ok", connectedState.sessionInfo)

            fsm.onStartStreaming()
            assertEquals(ConnectionState.Streaming, awaitItem())

            fsm.onStopStreaming()
            assertIs<ConnectionState.Connected>(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shouldHandleReconnectingWhenHeartbeatIsLostAndRestored() = runTest {
        val fsm = ConnectionStateMachine(scope = this)

        fsm.state.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())

            fsm.onScanQr(sampleConfig)
            awaitItem() // Pairing
            fsm.onHandshakeSuccess()
            awaitItem() // Connected
            fsm.onStartStreaming()
            awaitItem() // Streaming

            // Pérdida de heartbeat
            fsm.onHeartbeatLost()
            val reconnectingState = awaitItem()
            assertIs<ConnectionState.Reconnecting>(reconnectingState)

            // Restablecimiento de heartbeat antes del timeout
            fsm.onHeartbeatRestored()
            assertEquals(ConnectionState.Streaming, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shouldTimeoutAfter10SecondsInReconnectingAndTransitionToDisconnected() = runTest {
        val fsm = ConnectionStateMachine(scope = this, reconnectionTimeoutMs = 10_000L)

        fsm.state.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())

            fsm.onScanQr(sampleConfig)
            awaitItem() // Pairing
            fsm.onHandshakeSuccess()
            awaitItem() // Connected
            fsm.onStartStreaming()
            awaitItem() // Streaming

            fsm.onHeartbeatLost()
            assertIs<ConnectionState.Reconnecting>(awaitItem())

            // Avanzar el reloj virtual 10.001 ms para disparar el timeout automático
            advanceTimeBy(10_001L)
            runCurrent()

            assertEquals(ConnectionState.Disconnected, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shouldCleanlyDisconnectOnDisconnectRequestFromStreaming() = runTest {
        val fsm = ConnectionStateMachine(scope = this)

        fsm.state.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())

            fsm.onScanQr(sampleConfig)
            awaitItem() // Pairing
            fsm.onHandshakeSuccess()
            awaitItem() // Connected
            fsm.onStartStreaming()
            awaitItem() // Streaming

            fsm.onDisconnectRequest(reason = "USER_REQUEST")
            assertEquals(ConnectionState.Disconnected, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shouldTransitionToErrorOnInvalidTokenOrHandshakeFailure() = runTest {
        val fsm = ConnectionStateMachine(scope = this)

        fsm.state.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())

            fsm.onScanQr(sampleConfig)
            awaitItem() // Pairing

            val expectedError = ConnectionError(
                code = ConnectionErrorCode.TOKEN_EXPIRED,
                messageKey = "error_token_expired"
            )
            fsm.onHandshakeFailed(expectedError)

            val errorState = awaitItem()
            assertIs<ConnectionState.Error>(errorState)
            assertEquals(ConnectionErrorCode.TOKEN_EXPIRED, errorState.reason.code)

            // Reset tras error
            fsm.reset()
            assertEquals(ConnectionState.Disconnected, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
