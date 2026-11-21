package com.example.fridgecheck.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.fridgecheck.BuildConfig
import com.example.fridgecheck.data.CameraManager
import com.example.fridgecheck.data.GeminiManager
import com.example.fridgecheck.data.PantryManager
import com.example.fridgecheck.data.Recipe
import com.example.fridgecheck.data.RecipeService
import com.example.fridgecheck.data.calculateRecipeScore
import com.example.fridgecheck.data.cleanInputIngredient
import com.example.fridgecheck.data.countMissingIngredients
import com.example.fridgecheck.data.findOverlappingMeals
import com.example.fridgecheck.data.getIngredientSearchCandidates
import com.example.fridgecheck.data.shouldKeepRecipeForProteins
import com.example.fridgecheck.data.toFallbackRecipe
import com.example.fridgecheck.ui.theme.FridgeCheckTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screen destinations for the application.
 */
enum class AppScreen {
    HOME,
    CAMERA,
    INGREDIENTS,
    RECIPES
}

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

                // The raw list of names from Gemini or manual input
                var ingredientList by remember { mutableStateOf<List<String>>(emptyList()) }

                // The names the user has currently "checked"
                var selectedIngredients by remember { mutableStateOf(setOf<String>()) }

                // Default ingredients manager & persisted state
                val pantryManager = remember { PantryManager(context) }
                var defaultIngredients by remember { mutableStateOf(pantryManager.getDefaultIngredients()) }
                var showDefaultIngredientsSettings by remember { mutableStateOf(false) }

                // Navigation state
                var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
                var isSearchingRecipes by remember { mutableStateOf(false) }
                var isScanningImage by remember { mutableStateOf(false) }
                var frozenScanBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var searchErrorMessage by remember { mutableStateOf<String?>(null) }

                // Initialize Gemini
                val geminiManager = remember { GeminiManager(BuildConfig.GEMINI_API_KEY) }

                // Initialize Camera
                val cameraManager = remember { CameraManager(context) }

                val recipeService = remember {
                    retrofit2.Retrofit.Builder()
                        .baseUrl("https://www.themealdb.com/")
                        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                        .build()
                        .create(RecipeService::class.java)
                }

                // State to hold the recipe results
                var recipeResults by remember { mutableStateOf<List<Recipe>>(emptyList()) }

                val performSearch: () -> Unit = {
                    scope.launch {
                        isSearchingRecipes = true
                        searchErrorMessage = null

                        try {
                            val apiKey = BuildConfig.MEALDB_API_KEY.ifBlank { "1" }
                            val cleanIngredients = selectedIngredients.map { it.trim() }.filter { it.isNotEmpty() }

                            if (cleanIngredients.isEmpty()) {
                                searchErrorMessage = "No ingredients selected."
                                isSearchingRecipes = false
                                return@launch
                            }

                            // Query each ingredient separately using MealDB's free single-ingredient filter endpoint,
                            // trying candidate variations (plural/singular, synonyms, base proteins) for strict-match resiliency
                            val queryResults = coroutineScope {
                                cleanIngredients.map { ingredient ->
                                    async {
                                        val candidates = getIngredientSearchCandidates(ingredient)
                                        for (candidate in candidates) {
                                            try {
                                                val response = recipeService.filterByIngredient(apiKey, candidate)
                                                if (!response.meals.isNullOrEmpty()) {
                                                    return@async response.meals
                                                }
                                            } catch (e: Exception) {
                                                Log.w("FridgeCheck", "Failed to filter for $candidate", e)
                                            }
                                        }
                                        emptyList()
                                    }
                                }.awaitAll()
                            }

                            // Find recipes matching one or more ingredients, ranked by overlap count
                            val overlappingSummaries = findOverlappingMeals(queryResults)

                            if (overlappingSummaries.isEmpty()) {
                                searchErrorMessage = "No recipes found matching these ingredients."
                                recipeResults = emptyList()
                                isSearchingRecipes = false
                                return@launch
                            }

                            // Limit detail lookups to avoid excessive network requests (top 30)
                            val candidates = overlappingSummaries.take(30)
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

                            // Filtration: Only English characters in title, and filter out competing unowned proteins
                            val cleanRecipes = fullRecipes
                                .filter { recipe -> recipe.label.all { it.code < 128 } }
                                .filter { recipe -> shouldKeepRecipeForProteins(recipe, selectedIngredients) }

                            // Multifactor ranking:
                            // 1. Higher multifactor score (scanned matches, main course category boost, coverage)
                            // 2. Fewer missing ingredients as tie-breaker
                            val allOwnedIngredients = selectedIngredients + defaultIngredients
                            val sortedList = cleanRecipes.sortedWith(
                                compareByDescending<Recipe> { recipe ->
                                    calculateRecipeScore(recipe, selectedIngredients, defaultIngredients)
                                }.thenBy { recipe ->
                                    countMissingIngredients(recipe, allOwnedIngredients)
                                }
                            )

                            if (sortedList.isEmpty()) {
                                searchErrorMessage = "No recipes found for these items."
                                recipeResults = emptyList()
                            } else {
                                recipeResults = sortedList
                                currentScreen = AppScreen.RECIPES
                            }
                        } catch (e: Exception) {
                            searchErrorMessage = "Error: ${e.localizedMessage}"
                            Log.e("FridgeCheck", "Search failed", e)
                        } finally {
                            isSearchingRecipes = false
                        }
                    }
                }

                // If Default Ingredients settings is open, it overlays as a full page
                if (showDefaultIngredientsSettings) {
                    DefaultIngredientsPage(
                        defaultIngredients = defaultIngredients,
                        onIngredientsChange = { updated ->
                            defaultIngredients = updated
                            pantryManager.saveDefaultIngredients(updated)
                        },
                        onResetDefaults = {
                            val defaults = pantryManager.resetToDefaults()
                            defaultIngredients = defaults
                        },
                        onClose = { showDefaultIngredientsSettings = false }
                    )
                } else {
                    when (currentScreen) {
                        AppScreen.HOME -> {
                            HomeScreen(
                                defaultIngredientsCount = defaultIngredients.size,
                                onNavigateToScan = {
                                    cameraManager.resetController()
                                    resultText = "Ready to scan!"
                                    currentScreen = AppScreen.CAMERA
                                },
                                onNavigateToManual = {
                                    searchErrorMessage = null
                                    currentScreen = AppScreen.INGREDIENTS
                                },
                                onOpenDefaultIngredients = {
                                    showDefaultIngredientsSettings = true
                                }
                            )
                        }

                        AppScreen.CAMERA -> {
                            CameraScanScreen(
                                cameraManager = cameraManager,
                                resultText = resultText,
                                isScanning = isScanningImage,
                                frozenBitmap = frozenScanBitmap,
                                onBack = {
                                    if (!isScanningImage) {
                                        frozenScanBitmap = null
                                        currentScreen = AppScreen.HOME
                                    }
                                },
                                onCapturePhoto = { snapshot ->
                                    if (isScanningImage) return@CameraScanScreen
                                    frozenScanBitmap = snapshot
                                    scope.launch {
                                        isScanningImage = true
                                        resultText = "Scanning ingredients..."
                                        try {
                                            val bitmap = cameraManager.takePhoto()

                                            if (bitmap != null) {
                                                frozenScanBitmap = bitmap
                                                val response = geminiManager.analyzeFoodImage(bitmap)
                                                Log.d("GeminiRaw", "Response: $response")

                                                if (!response.isNullOrBlank() && !response.contains(
                                                        "NO_FOOD_DETECTED",
                                                        true
                                                    )
                                                ) {
                                                    if (response.length > 100) {
                                                        resultText = "I see things, but no food. Try a closer shot!"
                                                        return@launch
                                                    }

                                                    ingredientList = response.split(",")
                                                        .map { cleanInputIngredient(it) }
                                                        .filter { it.isNotEmpty() }

                                                    selectedIngredients = ingredientList.toSet()
                                                    currentScreen = AppScreen.INGREDIENTS
                                                    resultText = "Found ${ingredientList.size} items!"
                                                } else {
                                                    resultText = "No food detected. Make sure the lighting is good!"
                                                }
                                            } else {
                                                resultText = "Failed to capture photo. Please try again."
                                            }
                                        } catch (e: Exception) {
                                            resultText = "Error scanning image. Please try again."
                                            Log.e("FridgeCheck", "Photo scan error", e)
                                        } finally {
                                            isScanningImage = false
                                            frozenScanBitmap = null
                                        }
                                    }
                                },
                                onSwitchToManual = {
                                    if (!isScanningImage) {
                                        frozenScanBitmap = null
                                        searchErrorMessage = null
                                        currentScreen = AppScreen.INGREDIENTS
                                    }
                                }
                            )
                        }

                        AppScreen.INGREDIENTS -> {
                            IngredientSelectionPage(
                                ingredients = ingredientList,
                                selected = selectedIngredients,
                                defaultIngredientsCount = defaultIngredients.size,
                                isSearching = isSearchingRecipes,
                                errorMessage = searchErrorMessage,
                                onBack = {
                                    currentScreen = AppScreen.HOME
                                    searchErrorMessage = null
                                },
                                onOpenDefaultIngredients = { showDefaultIngredientsSettings = true },
                                onSelectionChange = {
                                    selectedIngredients = it
                                    searchErrorMessage = null
                                },
                                onAddIngredient = { newItem ->
                                    if (!ingredientList.contains(newItem)) {
                                        ingredientList = ingredientList + newItem
                                        selectedIngredients = selectedIngredients + newItem
                                        searchErrorMessage = null
                                    }
                                },
                                onRemoveIngredient = { item ->
                                    ingredientList = ingredientList - item
                                    selectedIngredients = selectedIngredients - item
                                    searchErrorMessage = null
                                },
                                onClearAll = {
                                    ingredientList = emptyList()
                                    selectedIngredients = emptySet()
                                    searchErrorMessage = null
                                },
                                onSearch = performSearch
                            )
                        }

                        AppScreen.RECIPES -> {
                            RecipeResultsPage(
                                recipes = recipeResults,
                                selectedIngredients = selectedIngredients,
                                defaultIngredients = defaultIngredients,
                                onBack = {
                                    currentScreen = AppScreen.INGREDIENTS
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home page with the app title and navigation to scan image or manually enter ingredients.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    defaultIngredientsCount: Int,
    onNavigateToScan: () -> Unit,
    onNavigateToManual: () -> Unit,
    onOpenDefaultIngredients: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fridge Check",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Hero banner card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🍳",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cook with what you have",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan items on your counter or in your kitchen, or type what you have to discover matching recipes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Action 1: Scan Food Image
            Card(
                onClick = onNavigateToScan,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📷", style = MaterialTheme.typography.titleLarge)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scan Ingredients",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Use camera to scan items with AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }

                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Main Action 2: Type Ingredients Manually
            Card(
                onClick = onNavigateToManual,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✍️", style = MaterialTheme.typography.titleLarge)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Type Ingredients Manually",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Directly enter items and search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Default Ingredients Card / Row
            Card(
                onClick = onOpenDefaultIngredients,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🧂", style = MaterialTheme.typography.titleMedium)
                        Column {
                            Text(
                                text = "Default Ingredients",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$defaultIngredientsCount assumed on hand",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Full page camera view for scanning food images.
 */
@Composable
fun CameraScanScreen(
    cameraManager: CameraManager,
    resultText: String,
    isScanning: Boolean = false,
    frozenBitmap: Bitmap? = null,
    onBack: () -> Unit,
    onCapturePhoto: (Bitmap?) -> Unit,
    onSwitchToManual: () -> Unit
) {
    BackHandler(onBack = onBack)
    val coroutineScope = rememberCoroutineScope()
    val flashAlpha = remember { Animatable(0f) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Bar (solid and not transparent)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularBackButton(onClick = onBack)

                Text(
                    text = resultText,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Balanced spacer equal to CircularBackButton size (40.dp) to keep title centered
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        // Camera viewfinder, freeze-frame, and flash animation strictly between the two solid bars
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
        ) {
            CameraPreview(
                controller = cameraManager.controller,
                modifier = Modifier.fillMaxSize(),
                onPreviewViewReady = { previewViewRef = it }
            )

            // Freeze-frame snapshot while scanning so preview never goes black
            val displayBitmap = frozenBitmap
            if (displayBitmap != null && isScanning) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Scanning frame",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Camera shutter flash animation overlay (only in the part between the two bars)
            if (flashAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAlpha.value))
                )
            }
        }

        // Bottom Bar (solid and not transparent)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        val snapshot = previewViewRef?.bitmap
                        coroutineScope.launch {
                            flashAlpha.snapTo(0.9f)
                            kotlinx.coroutines.delay(30.milliseconds)
                            flashAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
                            )
                        }
                        onCapturePhoto(snapshot)
                    },
                    enabled = !isScanning,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scanning...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Scan Ingredients",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSwitchToManual,
                    enabled = !isScanning,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "Type Ingredients Manually",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Full page for reviewing and adding ingredients manually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientSelectionPage(
    ingredients: List<String>,
    selected: Set<String>,
    defaultIngredientsCount: Int = 0,
    isSearching: Boolean = false,
    errorMessage: String? = null,
    onBack: () -> Unit,
    onOpenDefaultIngredients: (() -> Unit)? = null,
    onSelectionChange: (Set<String>) -> Unit,
    onAddIngredient: (String) -> Unit,
    onRemoveIngredient: (String) -> Unit,
    onClearAll: () -> Unit,
    onSearch: () -> Unit
) {
    BackHandler(onBack = onBack)

    var manualText by remember { mutableStateOf("") }

    val handleAdd = {
        if (manualText.isNotBlank()) {
            val items = manualText.split(",")
                .map { cleanInputIngredient(it) }
                .filter { it.isNotEmpty() }
            items.forEach { onAddIngredient(it) }
            manualText = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (ingredients.isEmpty()) "Add Ingredients" else "Review Ingredients",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                        CircularBackButton(onClick = onBack)
                    }
                },
                actions = {
                    if (ingredients.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear All")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onSearch,
                        enabled = selected.isNotEmpty() && !isSearching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Finding Recipes...")
                        } else {
                            Text(
                                if (selected.isEmpty()) "Add ingredients to search"
                                else "Find Recipes with ${selected.size} Item${if (selected.size == 1) "" else "s"}"
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = if (ingredients.isEmpty())
                    "Type the ingredients you have available to find matching recipes."
                else
                    "Tap to select or deselect items to tailor your recipe recommendations.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Narrower, compact pill bar for default ingredients count & edit button
            if (onOpenDefaultIngredients != null) {
                Surface(
                    onClick = onOpenDefaultIngredients,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🧂 Default ingredients: $defaultIngredientsCount active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text("Add one, or separate with ,") },
                    placeholder = { Text("Add one or separate with ,") },
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-4).dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleAdd() })
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = handleAdd,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Add")
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (ingredients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🥗",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No ingredients added yet.\nType ingredients above and tap Add!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ingredients (${selected.size} selected of ${ingredients.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
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
                            leadingIcon = if (isSelected) {
                                {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            } else null,
                            trailingIcon = {
                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clickable { onRemoveIngredient(item) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full page for displaying recommended recipes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeResultsPage(
    recipes: List<Recipe>,
    selectedIngredients: Set<String>,
    defaultIngredients: Set<String>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recommended Recipes", fontWeight = FontWeight.Bold)
                        Text(
                            "${recipes.size} recipe${if (recipes.size == 1) "" else "s"} found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                        CircularBackButton(onClick = onBack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(recipes.size) { index ->
                val recipe = recipes[index]
                RecipeCard(
                    recipe = recipe,
                    ownedIngredients = selectedIngredients + defaultIngredients,
                    onClick = {
                        val uri = recipe.url.toUri()
                        val defaultColors = CustomTabColorSchemeParams.Builder()
                            .setToolbarColor("#D84315".toColorInt())
                            .build()

                        val customTabsIntent = CustomTabsIntent.Builder()
                            .setDefaultColorSchemeParams(defaultColors)
                            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
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
                        .navigationBarsPadding()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Text("← Adjust Ingredients")
                    }

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
    }
}

@Composable
fun RecipeCard(recipe: Recipe, ownedIngredients: Set<String>, onClick: () -> Unit) {
    val missingCount = remember(recipe, ownedIngredients) {
        countMissingIngredients(recipe, ownedIngredients)
    }

    Card(
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
            AsyncImage(
                model = recipe.image,
                contentDescription = recipe.label,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (recipe.category.isNotBlank()) {
                    Text(
                        text = recipe.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = recipe.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
