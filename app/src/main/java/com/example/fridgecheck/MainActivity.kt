package com.example.fridgecheck

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fridgecheck.ui.theme.FridgeCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request Permission (One-time check)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)

        setContent {
            FridgeCheckTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                // 1. Define the controller ONCE here at the top
                val cameraController = remember {
                    LifecycleCameraController(context).apply {
                        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {

                        // 2. Pass that single controller into the preview
                        CameraPreview(
                            controller = cameraController,
                            lifecycleOwner = lifecycleOwner,
                            modifier = Modifier.fillMaxSize()
                        )

                        Button(
                            onClick = {
                                // 3. The magic happens here
                                takePhoto(cameraController, context) { bitmap ->
                                    // This is where we will eventually call Gemini!
                                    println("Captured bitmap of size: ${bitmap.width}x${bitmap.height}")
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(innerPadding)
                                .padding(bottom = 24.dp)
                        ) {
                            Text("Check Fridge")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                controller.bindToLifecycle(lifecycleOwner)
            }
        },
        modifier = modifier
    )
}

// 4. The helper function to capture the image
private fun takePhoto(
    controller: LifecycleCameraController,
    context: Context,
    onPhotoTaken: (Bitmap) -> Unit
) {
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // Convert the camera data to a Bitmap and close the proxy to save memory
                val bitmap = image.toBitmap()
                image.close()
                onPhotoTaken(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                println("Camera Error: ${exception.message}")
            }
        }
    )
}