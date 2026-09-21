package com.vcompanion.android.presentation.viewmodel

import app.cash.turbine.test
import com.vcompanion.shared.core.domain.model.CameraCapabilities
import com.vcompanion.shared.core.domain.model.CameraCommand
import com.vcompanion.shared.core.domain.model.ConnectionState
import com.vcompanion.shared.core.domain.model.DeviceMetrics
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.core.domain.model.ThermalState
import com.vcompanion.shared.core.domain.usecase.CameraCommandDispatcher
import com.vcompanion.shared.core.ports.IStreamGateway
import com.vcompanion.shared.core.ports.ITelemetryEmitter
import com.vcompanion.shared.core.protocol.ProtocolMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraStreamViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val incomingMessages = MutableSharedFlow<ProtocolMessage>(replay = 1, extraBufferCapacity = 64)
    private val telemetryFlow = MutableSharedFlow<DeviceMetrics>(replay = 1, extraBufferCapacity = 64)

    private val gateway = mockk<IStreamGateway>(relaxed = true)
    private val telemetryEmitter = mockk<ITelemetryEmitter>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { gateway.incomingMessages } returns incomingMessages.asSharedFlow()
        every { telemetryEmitter.telemetryFlow } returns telemetryFlow.asSharedFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shouldTransitionToStreamingStateWhenSessionStarts() = runTest(testDispatcher) {
        val viewModel = CameraStreamViewModel(
            streamGateway = gateway,
            telemetryEmitter = telemetryEmitter
        )

        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.connectionState is ConnectionState.Disconnected)

            val config = PairingConfig(
                host = "192.168.1.100",
                port = 8080,
                sessionToken = "tok_test_123"
            )
            viewModel.startSession(config)
            testDispatcher.scheduler.advanceUntilIdle()

            val activeState = expectMostRecentItem()
            assertTrue(activeState.connectionState is ConnectionState.Streaming)
        }
    }

    @Test
    fun shouldAutomaticallyReduceTo30FpsAndShowAlertOnSevereThermalState() = runTest(testDispatcher) {
        var appliedFps = 60
        val viewModel = CameraStreamViewModel(
            streamGateway = gateway,
            telemetryEmitter = telemetryEmitter,
            onSetFps = { appliedFps = it }
        )

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(60, initial.currentFps)
            assertFalse(initial.isThermalAlertActive)

            viewModel.startSession(PairingConfig(host = "10.0.0.1", port = 8080, sessionToken = "tok"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Emit thermal severe
            val severeMetrics = DeviceMetrics(
                fps = 60f,
                bitrateKbps = 3500L,
                latencyMs = 25L,
                batteryLevel = 45,
                isCharging = false,
                thermalState = ThermalState.SERIOUS
            )
            telemetryFlow.emit(severeMetrics)
            testDispatcher.scheduler.advanceUntilIdle()

            val throttledState = expectMostRecentItem()
            assertEquals(30, throttledState.currentFps)
            assertEquals(30, appliedFps)
            assertTrue(throttledState.isThermalAlertActive)
        }
    }

    @Test
    fun shouldExecuteTypedCameraCommandForSetZoom() = runTest(testDispatcher) {
        var appliedZoom = 1.0f
        val viewModel = CameraStreamViewModel(
            streamGateway = gateway,
            telemetryEmitter = telemetryEmitter,
            onApplyZoom = {
                appliedZoom = it
                true
            }
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.startSession(PairingConfig(host = "10.0.0.1", port = 8080, sessionToken = "tok"))
            testDispatcher.scheduler.advanceUntilIdle()

            incomingMessages.emit(ProtocolMessage.CommandPacket(CameraCommand.SetZoom(3.5f)))
            testDispatcher.scheduler.advanceUntilIdle()

            val zoomState = expectMostRecentItem()
            assertEquals(3.5f, zoomState.currentZoomRatio, 0.01f)
            assertEquals(3.5f, appliedZoom, 0.01f)
        }
    }

    @Test
    fun shouldExecuteToggleTorchOnlyWhenHardwareHasFlash() = runTest(testDispatcher) {
        var torchApplied = false
        val viewModelWithFlash = CameraStreamViewModel(
            streamGateway = gateway,
            telemetryEmitter = telemetryEmitter,
            hasFlashUnit = true,
            onToggleTorch = {
                torchApplied = it
                true
            }
        )

        viewModelWithFlash.uiState.test {
            awaitItem()
            viewModelWithFlash.startSession(PairingConfig(host = "10.0.0.1", port = 8080, sessionToken = "tok"))
            testDispatcher.scheduler.advanceUntilIdle()

            incomingMessages.emit(ProtocolMessage.CommandPacket(CameraCommand.ToggleTorch))
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertTrue(updatedState.isTorchEnabled)
            assertTrue(torchApplied)
        }

        val viewModelWithoutFlash = CameraStreamViewModel(
            streamGateway = gateway,
            telemetryEmitter = telemetryEmitter,
            hasFlashUnit = false,
            commandDispatcher = CameraCommandDispatcher(CameraCapabilities(hasTorch = false))
        )

        viewModelWithoutFlash.uiState.test {
            awaitItem()
            viewModelWithoutFlash.startSession(PairingConfig(host = "10.0.0.1", port = 8080, sessionToken = "tok"))
            testDispatcher.scheduler.advanceUntilIdle()

            incomingMessages.emit(ProtocolMessage.CommandPacket(CameraCommand.ToggleTorch))
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertFalse(updatedState.isTorchEnabled)
        }
    }

    @Test
    fun shouldSendDisconnectRequestAndTransitionToDisconnected() = runTest(testDispatcher) {
        coEvery { gateway.sendMessage(any()) } returns Result.success(Unit)

        val viewModel = CameraStreamViewModel(
            streamGateway = gateway,
            telemetryEmitter = telemetryEmitter
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.startSession(PairingConfig(host = "10.0.0.1", port = 8080, sessionToken = "tok"))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.disconnect()
            testDispatcher.scheduler.advanceUntilIdle()

            val disconnectedState = expectMostRecentItem()
            assertTrue(disconnectedState.connectionState is ConnectionState.Disconnected)

            coVerify { gateway.sendMessage(match { it is ProtocolMessage.DisconnectRequest }) }
            coVerify { gateway.disconnect() }
        }
    }
}
