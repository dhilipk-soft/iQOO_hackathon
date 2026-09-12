package com.studylens.input.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.resume

class CameraCapture(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Boolean = withContext(Dispatchers.Main) {
        Log.d(TAG, "Binding CameraX preview and ImageCapture use cases...")
        suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(previewView.display?.rotation ?: 0)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    Log.d(TAG, "CameraX successfully bound to lifecycle.")
                    continuation.resume(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind CameraX to lifecycle", e)
                    continuation.resume(false)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.IO) {
        val capture = imageCapture ?: run {
            Log.w(TAG, "ImageCapture is null, returning sample fallback bitmap.")
            return@withContext generateFallbackBitmap("Sample Textbook Page\nCalculus: Integration by Parts\n∫ u dv = uv - ∫ v du")
        }

        Log.d(TAG, "Taking picture frame...")
        suspendCancellableCoroutine<Bitmap?> { continuation ->
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            Log.d(TAG, "ImageProxy captured successfully (width=${image.width}, height=${image.height}).")
                            val bitmap = imageProxyToBitmap(image)
                            continuation.resume(bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
                            continuation.resume(null)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "CameraX takePicture error: ${exception.imageCaptureError}", exception)
                        continuation.resume(null)
                    }
                }
            )
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val rotationDegrees = image.imageInfo.rotationDegrees
        Log.d(TAG, "ImageProxy converted to Bitmap: ${bitmap.width}x${bitmap.height}, rotation=$rotationDegrees")
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    fun generateFallbackBitmap(sampleText: String): Bitmap {
        Log.d(TAG, "Generating fallback bitmap for sample text...")
        val width = 800
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
        }

        var y = 100f
        for (line in sampleText.split("\n")) {
            canvas.drawText(line, 50f, y, paint)
            y += 45f
        }
        return bitmap
    }

    companion object {
        private const val TAG = "CameraCapture"
    }
}

