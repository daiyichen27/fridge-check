// GeminiManager.kt
package com.example.fridgecheck.data

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

class GeminiManager(apiKey: String) {
    // 1. Initialize the model (Flash 1.5 is best for fast image reasoning)
    private val generativeModel = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = apiKey
    )

    // 2. The brain function
    suspend fun analyzeFoodImage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("GEMINI_DEBUG", "1. Starting Analysis...")

            val startTime = System.currentTimeMillis()
            val scaledBitmap = scaleDownForGemini(bitmap, maxDimension = 1024)
            Log.d(
                "GEMINI_DEBUG",
                "Original size: ${bitmap.width}x${bitmap.height}, Scaled size: ${scaledBitmap.width}x${scaledBitmap.height}"
            )

            val inputContent = content {
                image(scaledBitmap)
                text("Analyze this image. If you see food items or ingredients, list them separated by commas (e.g., Milk, Eggs). If the image does not contain food or ingredients, return exactly the phrase 'NO_FOOD_DETECTED'. Do not describe the image.")
            }

            Log.d("GEMINI_DEBUG", "2. Sending to Google Servers...")
            val response = generativeModel.generateContent(inputContent)
            val duration = System.currentTimeMillis() - startTime

            Log.d("GEMINI_DEBUG", "3. Success in ${duration}ms! Received: ${response.text}")
            response.text
        } catch (e: Exception) {
            // THIS LINE IS THE KEY: It prints the actual Google API error
            Log.e("GEMINI_DEBUG", "!!! GEMINI ERROR: ${e.localizedMessage}", e)
            null
        }
    }

    private fun scaleDownForGemini(bitmap: Bitmap, maxDimension: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width > height) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return bitmap.scale(newWidth, newHeight)
    }
}