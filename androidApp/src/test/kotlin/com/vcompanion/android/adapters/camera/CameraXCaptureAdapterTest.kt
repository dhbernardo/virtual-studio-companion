package com.vcompanion.android.adapters.camera

import android.util.Size
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CameraXCaptureAdapterTest {

    @Test
    fun shouldConfigureDefaultResolutionTo1080pAnd60Fps() {
        val adapter = CameraXCaptureAdapter(
            targetResolution = StreamResolution(1920, 1080),
            targetFps = 60
        )

        assertEquals(1920, adapter.targetResolution.width)
        assertEquals(1080, adapter.targetResolution.height)
        assertEquals(60, adapter.targetFps)
    }

    @Test
    fun shouldReduceTargetFpsWhenThermalMitigationTriggered() {
        val adapter = CameraXCaptureAdapter(
            targetResolution = StreamResolution(1920, 1080),
            targetFps = 60
        )

        adapter.setTargetFps(30)
        assertEquals(30, adapter.targetFps)
    }

    @Test
    fun shouldProcessFramesOnDedicatedThreadAndDeliverEncodedBytes() {
        val latch = CountDownLatch(1)
        var capturedThreadName = ""
        var receivedBytes: ByteArray? = null

        val fakeEncoder = object : VideoFrameEncoder {
            override fun encodeFrame(rawFrame: ByteArray): ByteArray {
                capturedThreadName = Thread.currentThread().name
                return rawFrame.reversedArray()
            }
            override fun release() {}
        }

        val adapter = CameraXCaptureAdapter(
            videoEncoder = fakeEncoder
        )

        val inputData = byteArrayOf(1, 2, 3, 4, 5)
        adapter.onFrameAvailable(inputData) { encoded ->
            receivedBytes = encoded
            latch.countDown()
        }

        assertTrue("Frame encoding timed out", latch.await(2, TimeUnit.SECONDS))
        assertTrue("Encoding should run on background worker thread", capturedThreadName.contains("CameraX-Worker") || capturedThreadName.contains("DefaultDispatcher"))
        assertEquals(5, receivedBytes?.size)
        assertEquals(5.toByte(), receivedBytes?.get(0))
        assertEquals(1.toByte(), receivedBytes?.get(4))

        adapter.release()
    }

    @Test
    fun shouldUpdateZoomRatioWithinLimits() {
        val adapter = CameraXCaptureAdapter()
        val zoomUpdated = adapter.applyZoomRatio(2.5f)
        assertTrue(zoomUpdated)
        assertEquals(2.5f, adapter.currentZoomRatio, 0.01f)
    }

    @Test
    fun shouldRejectZoomRatioOutsideLimits() {
        val adapter = CameraXCaptureAdapter()
        val zoomUpdatedLow = adapter.applyZoomRatio(0.5f)
        assertFalse(zoomUpdatedLow)

        val zoomUpdatedHigh = adapter.applyZoomRatio(15.0f)
        assertFalse(zoomUpdatedHigh)
    }
}
