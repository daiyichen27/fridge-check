package com.example.fridgecheck.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class CameraManager(
    private val context: Context,
) {
    private val captureExecutor = Executors.newSingleThreadExecutor()

    var controller: LifecycleCameraController by mutableStateOf(createController())
        private set

    private fun createController(): LifecycleCameraController {
        return LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    fun resetController(): LifecycleCameraController {
        try {
            controller.unbind()
        } catch (e: Exception) {
            Log.w("CameraManager", "Error unbinding old controller during reset", e)
        }
        val newController = createController()
        controller = newController
        return newController
    }

    suspend fun takePhoto(): Bitmap? {
        // Retry up to 4 attempts with short delays to allow the camera session to settle if just resumed
        for (attempt in 1..4) {
            val bitmap = suspendCancellableCoroutine { continuation ->
                try {
                    controller.takePicture(
                        captureExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val rotationDegrees = image.imageInfo.rotationDegrees
                                    val rawBitmap = image.toBitmap()
                                    val bmp = if (rotationDegrees != 0) {
                                        val matrix = Matrix().apply {
                                            postRotate(rotationDegrees.toFloat())
                                        }
                                        val rotated = Bitmap.createBitmap(
                                            rawBitmap,
                                            0,
                                            0,
                                            rawBitmap.width,
                                            rawBitmap.height,
                                            matrix,
                                            true
                                        )
                                        if (rotated != rawBitmap) {
                                            rawBitmap.recycle()
                                        }
                                        rotated
                                    } else {
                                        rawBitmap
                                    }
                                    image.close()
                                    continuation.resume(bmp)
                                } catch (e: Exception) {
                                    Log.e("CameraManager", "Failed to convert image to bitmap", e)
                                    image.close()
                                    continuation.resume(null)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.w(
                                    "CameraManager",
                                    "Capture attempt $attempt failed (code: ${exception.imageCaptureError}): ${exception.message}"
                                )
                                continuation.resume(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.w("CameraManager", "takePicture attempt $attempt threw exception: ${e.message}")
                    continuation.resume(null)
                }
            }

            if (bitmap != null) {
                return bitmap
            }

            if (attempt < 4) {
                kotlinx.coroutines.delay(400.milliseconds)
            }
        }
        return null
    }
}
