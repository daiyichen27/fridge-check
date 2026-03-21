// GeminiManager.kt
package com.example.fridgecheck.data

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiManager(apiKey: String) {
    // 1. Initialize the model (Flash 1.5 is best for fast image reasoning)
    private val generativeModel = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = apiKey
    )

    // 2. The brain function
    suspend fun analyzeFridgeImage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("GEMINI_DEBUG", "1. Starting Analysis...")

            val inputContent = content {
                image(bitmap)
                text("List food items in this fridge as a comma-separated list.")
            }

            Log.d("GEMINI_DEBUG", "2. Sending to Google Servers...")
            val response = generativeModel.generateContent(inputContent)

            Log.d("GEMINI_DEBUG", "3. Success! Received: ${response.text}")
            response.text
        } catch (e: Exception) {
            // THIS LINE IS THE KEY: It prints the actual Google API error
            Log.e("GEMINI_DEBUG", "!!! GEMINI ERROR: ${e.localizedMessage}", e)
            null
        }
    }
}