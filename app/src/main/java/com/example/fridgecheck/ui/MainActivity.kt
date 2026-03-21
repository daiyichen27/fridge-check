package com.example.fridgecheck.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.fridgecheck.data.CameraManager
import com.example.fridgecheck.data.GeminiManager
import com.example.fridgecheck.ui.theme.FridgeCheckTheme
import kotlinx.coroutines.launch
import com.example.fridgecheck.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        // Request Permission (One-time check)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)

        setContent {
            FridgeCheckTheme {
                val context = LocalContext.current

                val scope = rememberCoroutineScope()
                var resultText by remember { mutableStateOf("Ready to scan!") }

                // Initialize Gemini
                val geminiManager = remember { GeminiManager(BuildConfig.GEMINI_API_KEY) }

                // Initialize Camera
                val cameraManager = remember {
                    CameraManager(context) { bitmap ->
                        // When photo is ready, trigger Gemini in a background thread
                        scope.launch {
                            resultText = "Gemini is looking..."
                            val ingredients = geminiManager.analyzeFridgeImage(bitmap)
                            resultText = ingredients ?: "Error analyzing image"
                        }
                    }
                }

                Scaffold { innerPadding ->
                    Box {
                        CameraPreview(
                            controller = cameraManager.controller,
                            modifier = Modifier.fillMaxSize()
                        )

                        Button(
                            onClick = { cameraManager.takePhoto() }, // Clean call!
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = innerPadding.calculateBottomPadding() + 128.dp)
                        ) {
                            Text("Check Fridge")
                        }

                        Text(
                            text = resultText,
                            modifier = Modifier
                                .align(Alignment.TopCenter) // Centers it horizontally at the top
                                .padding(top = 100.dp)      // Pushes it down below the camera/status bar
                                .padding(horizontal = 24.dp), // Keeps it away from the side edges
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, // Centers the lines of text
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge // Makes it more readable
                        )
                    }
                }
            }
        }
    }
}