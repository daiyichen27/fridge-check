package com.example.fridgecheck.ui

import android.util.Log
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier,
    onPreviewViewReady: ((PreviewView) -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                try {
                    controller.bindToLifecycle(lifecycleOwner)
                } catch (e: Exception) {
                    Log.w("CameraPreview", "Rebind on resume: ${e.message}")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        try {
            controller.bindToLifecycle(lifecycleOwner)
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to bind camera controller", e)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                controller.unbind()
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to unbind camera on dispose", e)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
                onPreviewViewReady?.invoke(this)
            }
        },
        update = { previewView ->
            if (previewView.controller != controller) {
                previewView.controller = controller
            }
            onPreviewViewReady?.invoke(previewView)
        },
        modifier = modifier
    )
}
