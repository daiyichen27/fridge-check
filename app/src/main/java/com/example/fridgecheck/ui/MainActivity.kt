package com.example.fridgecheck.ui

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.fridgecheck.BuildConfig
import com.example.fridgecheck.data.CameraManager
import com.example.fridgecheck.data.GeminiManager
import com.example.fridgecheck.ui.theme.FridgeCheckTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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

                // The raw list of names from Gemini
                var ingredientList by remember { mutableStateOf<List<String>>(emptyList()) }

                // The names the user has currently "checked"
                var selectedIngredients by remember { mutableStateOf(setOf<String>()) }

                // A flag to show/hide the selection menu
                var showSelectionMenu by remember { mutableStateOf(false) }

                // Initialize Gemini
                val geminiManager = remember { GeminiManager(BuildConfig.GEMINI_API_KEY) }

                // Initialize Camera
                val cameraManager = remember {
                    CameraManager(context)
                }

                Scaffold { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        CameraPreview(
                            controller = cameraManager.controller,
                            modifier = Modifier.fillMaxSize()
                        )

                        Button(
                            onClick = { // We use a scope because Gemini takes a few seconds to "think"
                                scope.launch {
                                    resultText = "Scanning your fridge..."
                                    // 1. Trigger the camera and get the resulting bitmap
                                    val bitmap = cameraManager.takePhoto()

                                    if (bitmap != null) {
                                        // 2. Send the image to our GeminiManager
                                        val response = geminiManager.analyzeFridgeImage(bitmap)

                                        // 3. Updated Validation Logic
                                        if (!response.isNullOrBlank() && !response.contains(
                                                "NO_FOOD_DETECTED",
                                                true
                                            )
                                        ) {
                                            // Check if Gemini returned a long sentence (which means it's describing the room)
                                            // Real ingredient lists are usually short.
                                            // If the response is more than 100 characters, it's probably a description we should ignore.
                                            if (response.length > 100) {
                                                resultText =
                                                    "I see things, but no food. Try a closer shot!"
                                                return@launch
                                            }

                                            ingredientList = response.split(",")
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }

                                            selectedIngredients = ingredientList.toSet()
                                            showSelectionMenu = true
                                            resultText = "Found ${ingredientList.size} items!"
                                        } else {
                                            // This triggers if Gemini says NO_FOOD_DETECTED or returns null
                                            resultText =
                                                "No food detected. Make sure the lighting is good!"
                                        }
                                    }
                                }
                            },
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
                            style = MaterialTheme.typography.titleLarge // Makes it more readable
                        )
                    }

                    if (showSelectionMenu) {
                        ModalBottomSheet(
                            onDismissRequest = { showSelectionMenu = false },
                            sheetState = rememberModalBottomSheetState()
                        ) {
                            // FlowRow, Chips, and "Search Recipes" Button code goes here
                            IngredientSelectionContent(
                                ingredients = ingredientList,
                                selected = selectedIngredients,
                                onSelectionChange = { selectedIngredients = it },
                                onAddIngredient = { newItem ->
                                    if (!ingredientList.contains(newItem)) {
                                        ingredientList = ingredientList + newItem
                                        selectedIngredients = selectedIngredients + newItem
                                    }
                                },
                                onSearch = {
                                    showSelectionMenu = false
                                    // This is where we will eventually trigger the Spoonacular API
                                    Log.d(
                                        "FridgeCheck",
                                        "Searching recipes for: $selectedIngredients"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // Required for FlowRow in 2026
@Composable
fun IngredientSelectionContent(
    ingredients: List<String>,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onAddIngredient: (String) -> Unit,
    onSearch: () -> Unit
) {
    var manualText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding() // Keeps it above the Samsung gesture bar
    ) {
        Text(
            text = "Verify Ingredients",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tap to deselect items you don't want to use.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = manualText,
                onValueChange = { manualText = it },
                label = { Text("Add missing item") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (manualText.isNotBlank()) {
                        onAddIngredient(manualText.trim())
                        manualText = "" // Clear input
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // This handles the "wrapping" of buttons so they don't go off-screen
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()), // In case Gemini finds 50 items!
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ingredients.forEach { item ->
                val isSelected = selected.contains(item)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) onSelectionChange(selected - item)
                        else onSelectionChange(selected + item)
                    },
                    label = { Text(item) },
                    // Replace the Icon block with this simple Text block
                    leadingIcon = if (isSelected) {
                        {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Find Recipes with ${selected.size} Items")
        }
    }
}