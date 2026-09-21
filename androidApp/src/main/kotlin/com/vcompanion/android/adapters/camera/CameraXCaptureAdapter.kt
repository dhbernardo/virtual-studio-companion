package com.vcompanion.android.adapters.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vcompanion.shared.core.domain.model.LensFacing
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class StreamResolution(
    val width: Int = 1920,
    val height: Int = 1080
) {
    companion object {
        val FHD_1080P = StreamResolution(1920, 1080)
        val HD_720P = StreamResolution(1280, 720)
    }
}

interface VideoFrameEncoder {
    fun encodeFrame(rawFrame: ByteArray): ByteArray
    fun release()
}

class JpegVideoFrameEncoder(
    private val quality: Int = 75
) : VideoFrameEncoder {
    override fun encodeFrame(rawFrame: ByteArray): ByteArray = rawFrame

    fun encodeImageProxy(imageProxy: ImageProxy): ByteArray? {
        return try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            val finalBitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
            val stream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()
            if (finalBitmap !== bitmap) {
                finalBitmap.recycle()
            }
            bitmap.recycle()
            bytes
        } catch (_: Throwable) {
            null
        }
    }

    override fun release() {}
}

class H264MediaCodecEncoder(
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val bitrate: Int = 4_000_000,
    private val fps: Int = 60
) : VideoFrameEncoder {

    private var codec: MediaCodec? = null

    init {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            codec = encoder
        } catch (_: Throwable) {
            codec = null
        }
    }

    override fun encodeFrame(rawFrame: ByteArray): ByteArray {
        val activeCodec = codec ?: return rawFrame
        return try {
            val inputIndex = activeCodec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer: ByteBuffer? = activeCodec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(rawFrame, 0, minOf(rawFrame.size, inputBuffer.remaining()))
                activeCodec.queueInputBuffer(
                    inputIndex,
                    0,
                    minOf(rawFrame.size, inputBuffer?.capacity() ?: rawFrame.size),
                    System.nanoTime() / 1000,
                    0
                )
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = activeCodec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                val outputBuffer: ByteBuffer? = activeCodec.getOutputBuffer(outputIndex)
                val outData = ByteArray(bufferInfo.size)
                outputBuffer?.get(outData)
                activeCodec.releaseOutputBuffer(outputIndex, false)
                outData
            } else {
                rawFrame
            }
        } catch (_: Throwable) {
            rawFrame
        }
    }

    override fun release() {
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Throwable) {
            // Ignore on cleanup
        } finally {
            codec = null
        }
    }
}

class CameraXCaptureAdapter(
    var targetResolution: StreamResolution = StreamResolution.FHD_1080P,
    targetFps: Int = 60,
    private val videoEncoder: VideoFrameEncoder = H264MediaCodecEncoder(
        width = targetResolution.width,
        height = targetResolution.height,
        fps = targetFps
    ),
    private val jpegEncoder: JpegVideoFrameEncoder = JpegVideoFrameEncoder(quality = 75),
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CameraX-Worker").apply { priority = Thread.MAX_PRIORITY }
    }
) {
    var targetFps: Int = targetFps
        private set

    var currentZoomRatio: Float = 1.0f
        private set

    var isTorchEnabled: Boolean = false
        private set

    var currentLensFacing: LensFacing = LensFacing.BACK
        private set

    @Volatile
    var currentFps: Float = 0f
        private set

    @Volatile
    var currentBitrateKbps: Long = 0L
        private set

    private var frameCountWindow = 0
    private var bytesCountWindow = 0L
    private var windowStartMs = System.currentTimeMillis()

    @Synchronized
    fun recordEncodedFrame(byteCount: Int) {
        frameCountWindow++
        bytesCountWindow += byteCount
        val now = System.currentTimeMillis()
        val elapsed = now - windowStartMs
        if (elapsed >= 1000L) {
            currentFps = (frameCountWindow * 1000f) / elapsed
            currentBitrateKbps = (bytesCountWindow * 8L) / elapsed
            frameCountWindow = 0
            bytesCountWindow = 0L
            windowStartMs = now
        }
    }

    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var cameraProvider: ProcessCameraProvider? = null

    fun setTargetFps(fps: Int) {
        this.targetFps = fps
    }

    fun applyZoomRatio(ratio: Float): Boolean {
        if (ratio < 1.0f || ratio > 10.0f) {
            return false
        }
        currentZoomRatio = ratio
        cameraControl?.setZoomRatio(ratio)
        return true
    }

    fun toggleTorch(hasFlashUnit: Boolean): Boolean {
        if (!hasFlashUnit) return false
        isTorchEnabled = !isTorchEnabled
        cameraControl?.enableTorch(isTorchEnabled)
        return true
    }

    fun onFrameAvailable(rawFrame: ByteArray, onEncoded: (ByteArray) -> Unit) {
        backgroundExecutor.execute {
            val encoded = videoEncoder.encodeFrame(rawFrame)
            onEncoded(encoded)
        }
    }

    fun startCapture(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onFrameEncoded: (ByteArray) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val targetSize = Size(targetResolution.width, targetResolution.height)
            val preview = Preview.Builder()
                .setTargetResolution(targetSize)
                .build()
                .also {
                    it.setSurfaceProvider(surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(targetSize)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(backgroundExecutor) { imageProxy ->
                        try {
                            val jpegBytes = jpegEncoder.encodeImageProxy(imageProxy)
                            if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                                recordEncodedFrame(jpegBytes.size)
                                onFrameEncoded(jpegBytes)
                            } else {
                                val planes = imageProxy.planes
                                if (planes.isNotEmpty()) {
                                    val buffer = planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)
                                    val encoded = videoEncoder.encodeFrame(bytes)
                                    recordEncodedFrame(encoded.size)
                                    onFrameEncoded(encoded)
                                }
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = if (currentLensFacing == LensFacing.BACK) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
            } catch (_: Exception) {
                // Ignore or report
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCapture() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun release() {
        stopCapture()
        videoEncoder.release()
        backgroundExecutor.shutdown()
    }
}
