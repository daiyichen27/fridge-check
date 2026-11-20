package com.example.fridgecheck.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.fridgecheck.BuildConfig
import com.example.fridgecheck.data.CameraManager
import com.example.fridgecheck.data.GeminiManager
import com.example.fridgecheck.data.Recipe
import com.example.fridgecheck.data.RecipeService
import com.example.fridgecheck.data.countMissingIngredients
import com.example.fridgecheck.data.findOverlappingMeals
import com.example.fridgecheck.data.toFallbackRecipe
import com.example.fridgecheck.ui.theme.FridgeCheckTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt

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

                var showRecipeResults by remember { mutableStateOf(false) }

                // Initialize Gemini
                val geminiManager = remember { GeminiManager(BuildConfig.GEMINI_API_KEY) }

                // Initialize Camera
                val cameraManager = remember {
                    CameraManager(context)
                }

                val recipeService = remember {
                    retrofit2.Retrofit.Builder()
                        .baseUrl("https://www.themealdb.com/")
                        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                        .build()
                        .create(RecipeService::class.java)
                }

                // And a state to hold the results
                var recipeResults by remember { mutableStateOf<List<Recipe>>(emptyList()) }

                Scaffold { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        CameraPreview(
                            controller = cameraManager.controller,
                            modifier = Modifier.fillMaxSize()
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = innerPadding.calculateBottomPadding() + 64.dp)
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { // We use a scope because Gemini takes a few seconds to "think"
                                    scope.launch {
                                        resultText = "Scanning ingredients..."
                                        // 1. Trigger the camera and get the resulting bitmap
                                        val bitmap = cameraManager.takePhoto()

                                        if (bitmap != null) {
                                            // 2. Send the image to our GeminiManager
                                            val response = geminiManager.analyzeFoodImage(bitmap)

                                            Log.d("GeminiRaw", "Response: $response")
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
                                modifier = Modifier.fillMaxWidth(0.75f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Scan Ingredients")
                            }

                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    showSelectionMenu = true
                                },
                                modifier = Modifier.fillMaxWidth(0.75f),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.55f),
                                    contentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
                            ) {
                                Text("Type Ingredients Manually")
                            }
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
                                    scope.launch {
                                        showSelectionMenu = false
                                        resultText = "Finding the best recipes..."

                                        try {
                                            val apiKey = BuildConfig.MEALDB_API_KEY.ifBlank { "1" }
                                            val cleanIngredients = selectedIngredients.map { it.trim() }.filter { it.isNotEmpty() }

                                            if (cleanIngredients.isEmpty()) {
                                                resultText = "No ingredients selected."
                                                recipeResults = emptyList()
                                                return@launch
                                            }

                                            // Query each ingredient separately using MealDB's free single-ingredient filter endpoint
                                            val queryResults = coroutineScope {
                                                cleanIngredients.map { ingredient ->
                                                    async {
                                                        try {
                                                            val response = recipeService.filterByIngredient(apiKey, ingredient)
                                                            response.meals ?: emptyList()
                                                        } catch (e: Exception) {
                                                            Log.e("FridgeCheck", "Failed to filter for $ingredient", e)
                                                            emptyList()
                                                        }
                                                    }
                                                }.awaitAll()
                                            }

                                            // Find recipes that overlap across multiple ingredient queries
                                            val overlappingSummaries = findOverlappingMeals(queryResults)

                                            if (overlappingSummaries.isEmpty()) {
                                                resultText = "No overlapping recipes found for these items."
                                                recipeResults = emptyList()
                                                return@launch
                                            }

                                            // Limit detail lookups to avoid excessive network requests (top 25)
                                            val candidates = overlappingSummaries.take(25)
                                            val fullRecipes = coroutineScope {
                                                candidates.map { summary ->
                                                    async {
                                                        try {
                                                            val detailResponse = recipeService.lookupMealById(apiKey, summary.idMeal)
                                                            val detail = detailResponse.meals?.firstOrNull()
                                                            detail?.toRecipe() ?: summary.toFallbackRecipe()
                                                        } catch (e: Exception) {
                                                            Log.w("FridgeCheck", "Failed lookup for ${summary.idMeal}, falling back", e)
                                                            summary.toFallbackRecipe()
                                                        }
                                                    }
                                                }.awaitAll()
                                            }

                                            // Filtration: Only English characters in title
                                            val cleanRecipes = fullRecipes.filter { recipe ->
                                                recipe.label.all { it.code < 128 }
                                            }

                                            val sortedList = cleanRecipes.sortedBy { recipe ->
                                                countMissingIngredients(recipe, selectedIngredients)
                                            }

                                            if (sortedList.isEmpty()) {
                                                // 1. The Recipe BottomSheet stays HIDDEN (showRecipeResults remains false)
                                                resultText = "No recipes found for these items."

                                                // 2. We clear any old results so the UI doesn't show "ghost" data
                                                recipeResults = emptyList()
                                            } else {
                                                // This ONLY runs if there is at least one recipe
                                                recipeResults = sortedList
                                                showRecipeResults = true
                                                resultText = "Found ${sortedList.size} recipes!"
                                            }
                                        } catch (e: Exception) {
                                            resultText = "Error: ${e.localizedMessage}"
                                            Log.e("FridgeCheck", "Search failed", e)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    if (showRecipeResults) {
                        ModalBottomSheet(
                            onDismissRequest = {
                                showRecipeResults = false
                                // Reset the text back to the starting message
                                resultText = "Ready to scan!"
                            },
                            sheetState = rememberModalBottomSheetState()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .navigationBarsPadding() // Crucial for S25 Ultra gesture bar
                            ) {
                                Text(
                                    text = "Recommended Recipes",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // THE LAZY COLUMN GOES HERE
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(recipeResults.size) { index ->
                                        val recipe = recipeResults[index]
                                        RecipeCard(
                                            recipe = recipe,
                                            ownedIngredients = selectedIngredients,
                                            onClick = {
                                                val uri = recipe.url.toUri()

                                                // 1. Create the color scheme (Non-deprecated way)
                                                val defaultColors = CustomTabColorSchemeParams.Builder()
                                                    .setToolbarColor("#D84315".toColorInt()) // TheMealDB Orange/Warm Red
                                                    .build()

                                                // 2. Build the Intent with the color scheme
                                                val customTabsIntent = CustomTabsIntent.Builder()
                                                    .setDefaultColorSchemeParams(defaultColors)
                                                    .setShareState(CustomTabsIntent.SHARE_STATE_ON) // Adds a share button inside the browser!
                                                    .setShowTitle(true)
                                                    .build()

                                                customTabsIntent.launchUrl(context, uri)
                                            }
                                        )
                                    }
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        val intent = Intent(
                                                            Intent.ACTION_VIEW,
                                                            "https://www.themealdb.com".toUri()
                                                        )
                                                        context.startActivity(intent)
                                                    }
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = "Recipe data & imagery by TheMealDB",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }
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

    val handleAdd = {
        if (manualText.isNotBlank()) {
            val items = manualText.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            items.forEach { onAddIngredient(it) }
            manualText = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding() // Keeps it above the Samsung gesture bar
    ) {
        Text(
            text = if (ingredients.isEmpty()) "Enter Ingredients" else "Verify Ingredients",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (ingredients.isEmpty())
                "Type your ingredients to find matching recipes."
            else
                "Tap to deselect items you don't want to use.",
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
                label = { Text("Add ingredient (e.g. Chicken)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { handleAdd() }
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = handleAdd,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (ingredients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No ingredients added yet.\nType ingredients above and tap Add!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
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
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSearch,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (selected.isEmpty()) "Add ingredients to search"
                else "Find Recipes with ${selected.size} Items"
            )
        }
    }
}

@Composable
fun RecipeCard(recipe: Recipe, ownedIngredients: Set<String>, onClick: () -> Unit) {
    val missingCount = remember(recipe, ownedIngredients) {
        val matches = recipe.ingredientLines.count { line ->
            ownedIngredients.any { owned -> line.contains(owned, ignoreCase = true) }
        }
        (recipe.ingredientLines.size - matches).coerceAtLeast(0)
    }

    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // THE IMAGE COMPONENT
            coil.compose.AsyncImage(
                model = recipe.image,
                contentDescription = recipe.label,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                val isPerfect = missingCount == 0
                Text(
                    text = if (isPerfect) "★ Perfect Match" else "Missing $missingCount items",
                    color = if (isPerfect) Color(0xFF4CAF50) else Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}