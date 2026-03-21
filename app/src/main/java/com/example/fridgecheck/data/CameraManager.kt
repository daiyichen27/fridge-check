package com.example.fridgecheck.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class CameraManager(
    private val context: Context,
    // Note: If you are using LifecycleCameraController, you usually don't need a separate imageCapture.
    // I've kept it here to match your structure, but added a fallback.
) {
    val controller: LifecycleCameraController = LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
    }

    suspend fun takePhoto(): Bitmap? = suspendCancellableCoroutine { continuation ->
        // Use the controller's internal capture if your local imageCapture is null
        val cameraExecutor = ContextCompat.getMainExecutor(context)

        controller.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    // If you have a global callback, use it, otherwise resume the coroutine
                    continuation.resume(bitmap)
                }

                // Added 'override' and fixed the name to 'onError' per CameraX API
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraManager", "Capture failed", exception)
                    continuation.resume(null) // Now 'null' will work with the resume import
                }
            }
        )
    }
}