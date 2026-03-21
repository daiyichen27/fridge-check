package com.example.fridgecheck.data

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat

/**
 * Encapsulates all CameraX logic to keep MainActivity clean.
 */
class CameraManager(
    private val context: Context,
    private val onPhotoCaptured: (Bitmap) -> Unit
) {
    // 1. Initialize the controller
    val controller: LifecycleCameraController = LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
    }

    // 2. The takePhoto logic
    fun takePhoto() {
        controller.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    onPhotoCaptured(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            }
        )
    }
}