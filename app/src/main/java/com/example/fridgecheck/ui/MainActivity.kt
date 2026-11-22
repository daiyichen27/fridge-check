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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                var isScanResult by remember { mutableStateOf(false) }
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
                                    isScanResult = false
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
                                                    isScanResult = true
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
                                        isScanResult = false
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
                                isScanResult = isScanResult,
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
                                    isScanResult = false
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
/**
 * Home page with the playful bento layout, cartoon fridge mascot, and navigation to scan image or manually enter ingredients.
 */
@Composable
fun HomeScreen(
    defaultIngredientsCount: Int,
    onNavigateToScan: () -> Unit,
    onNavigateToManual: () -> Unit,
    onOpenDefaultIngredients: () -> Unit
) {
    val warmBg = Color(0xFFFFFBF0)         // Warm soft butter background
    val cardBg = Color.White
    val vibrantOrange = Color(0xFFFF7A00)  // Energetic warm orange
    val mintGreen = Color(0xFF10B981)      // Fresh kitchen mint
    val darkPlum = Color(0xFF2D143D)       // Rich deep plum text
    val softGrayText = Color(0xFF6B5E70)   // Softer purple-gray secondary text
    val warmTint = Color(0xFFFFECD1)       // Soft apricot icon background

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(warmBg),
        color = warmBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header: Title & 3-dot overflow menu button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fridge Check",
                    fontFamily = FontFamily.Cursive,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = vibrantOrange
                )

                // 3-dot overflow menu button (ready for future dropdown implementation)
                Surface(
                    shape = CircleShape,
                    color = warmTint,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val dotR = 2.2.dp.toPx()
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val spacing = 6.dp.toPx()

                            drawCircle(vibrantOrange, radius = dotR, center = Offset(cx, cy - spacing))
                            drawCircle(vibrantOrange, radius = dotR, center = Offset(cx, cy))
                            drawCircle(vibrantOrange, radius = dotR, center = Offset(cx, cy + spacing))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Cartoon Fridge Mascot Illustration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(width = 190.dp, height = 230.dp)) {
                    val strokeW = 3.dp.toPx()
                    val fridgeBody = Color(0xFFF0FDF4) // soft cool minty white
                    val fridgeOutline = darkPlum
                    val handleColor = vibrantOrange
                    val magnetRed = Color(0xFFF43F5E) // strawberry magnet
                    val magnetYellow = Color(0xFFFDE047) // cheese magnet
                    val magnetMint = Color(0xFF34D399) // mint note magnet
                    val blushColor = Color(0xFFFFB4A2)

                    val w = size.width * 0.88f
                    val h = size.height * 0.90f
                    val left = (size.width - w) / 2f
                    val top = 6.dp.toPx()

                    // Main Fridge Body
                    drawRoundRect(
                        color = fridgeBody,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(26.dp.toPx())
                    )
                    drawRoundRect(
                        color = fridgeOutline,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(26.dp.toPx()),
                        style = Stroke(strokeW)
                    )

                    // Freezer / Fridge door separation line
                    val splitY = top + h * 0.38f
                    drawLine(
                        color = fridgeOutline,
                        start = Offset(left, splitY),
                        end = Offset(left + w, splitY),
                        strokeWidth = strokeW
                    )

                    // Upper door handle
                    drawLine(
                        color = handleColor,
                        start = Offset(left + 11.dp.toPx(), top + 16.dp.toPx()),
                        end = Offset(left + 11.dp.toPx(), splitY - 16.dp.toPx()),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Lower door handle
                    drawLine(
                        color = handleColor,
                        start = Offset(left + 11.dp.toPx(), splitY + 18.dp.toPx()),
                        end = Offset(left + 11.dp.toPx(), splitY + h * 0.42f),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Kawaii face on freezer door
                    val eyeY = top + h * 0.19f
                    val eyeLeftX = left + w * 0.44f
                    val eyeRightX = left + w * 0.74f
                    drawCircle(fridgeOutline, radius = 5.dp.toPx(), center = Offset(eyeLeftX, eyeY))
                    drawCircle(fridgeOutline, radius = 5.dp.toPx(), center = Offset(eyeRightX, eyeY))

                    // Eye glints (dual sparkles)
                    drawCircle(Color.White, radius = 1.8.dp.toPx(), center = Offset(eyeLeftX - 1.5.dp.toPx(), eyeY - 1.5.dp.toPx()))
                    drawCircle(Color.White, radius = 1.8.dp.toPx(), center = Offset(eyeRightX - 1.5.dp.toPx(), eyeY - 1.5.dp.toPx()))
                    drawCircle(Color.White, radius = 0.8.dp.toPx(), center = Offset(eyeLeftX + 1.8.dp.toPx(), eyeY + 1.8.dp.toPx()))
                    drawCircle(Color.White, radius = 0.8.dp.toPx(), center = Offset(eyeRightX + 1.8.dp.toPx(), eyeY + 1.8.dp.toPx()))

                    // Big rosy blushing cheeks
                    drawCircle(blushColor, radius = 6.5.dp.toPx(), center = Offset(eyeLeftX - 7.dp.toPx(), eyeY + 6.dp.toPx()))
                    drawCircle(blushColor, radius = 6.5.dp.toPx(), center = Offset(eyeRightX + 7.dp.toPx(), eyeY + 6.dp.toPx()))

                    // Smiling happy mouth arc
                    drawArc(
                        color = fridgeOutline,
                        startAngle = 10f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(left + w * 0.52f, eyeY + 1.dp.toPx()),
                        size = Size(w * 0.15f, 10.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Cute magnets on lower door
                    // 1. Strawberry/heart magnet
                    drawCircle(magnetRed, radius = 6.dp.toPx(), center = Offset(left + w * 0.74f, splitY + 30.dp.toPx()))
                    // 2. Square yellow post-it note
                    drawRoundRect(
                        color = magnetYellow,
                        topLeft = Offset(left + w * 0.44f, splitY + 48.dp.toPx()),
                        size = Size(18.dp.toPx(), 18.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                    drawRoundRect(
                        color = fridgeOutline,
                        topLeft = Offset(left + w * 0.44f, splitY + 48.dp.toPx()),
                        size = Size(18.dp.toPx(), 18.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        style = Stroke(1.2.dp.toPx())
                    )
                    // Note pin dot
                    drawCircle(darkPlum, radius = 1.5.dp.toPx(), center = Offset(left + w * 0.44f + 9.dp.toPx(), splitY + 51.dp.toPx()))

                    // 3. Round mint magnet
                    drawCircle(magnetMint, radius = 4.5.dp.toPx(), center = Offset(left + w * 0.78f, splitY + 84.dp.toPx()))

                    // Sturdy standing fridge feet
                    val footW = 12.dp.toPx()
                    val footH = 7.dp.toPx()
                    drawRoundRect(
                        color = fridgeOutline,
                        topLeft = Offset(left + 12.dp.toPx(), top + h - 1.dp.toPx()),
                        size = Size(footW, footH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    drawRoundRect(
                        color = fridgeOutline,
                        topLeft = Offset(left + w - 12.dp.toPx() - footW, top + h - 1.dp.toPx()),
                        size = Size(footW, footH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bento Box 1: Scan Hero Card (Large top card with playful vector camera)
            Card(
                onClick = onNavigateToScan,
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = warmTint
                            ) {
                                Text(
                                    text = "AI VISION DETECT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = vibrantOrange,
                                    letterSpacing = 0.8f.sp,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Scan Food",
                                fontFamily = FontFamily.Serif,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = darkPlum
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Detailed Vector Camera Icon (Centered in circle)
                        Surface(
                            shape = CircleShape,
                            color = warmTint,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.size(30.dp)) {
                                    val strokeW = 1.6.dp.toPx()
                                    val bodyColor = Color(0xFFF8FAFC)
                                    val gripColor = vibrantOrange
                                    val metalColor = Color(0xFF94A3B8)
                                    val darkOutline = darkPlum

                                    val w = size.width * 0.88f
                                    val h = size.height * 0.62f
                                    val left = (size.width - w) / 2f
                                    val top = (size.height - h) / 2f + 1.dp.toPx()

                                    // 1. Camera Body (rounded rectangle)
                                    drawRoundRect(
                                        color = bodyColor,
                                        topLeft = Offset(left, top),
                                        size = Size(w, h),
                                        cornerRadius = CornerRadius(6.dp.toPx())
                                    )

                                    // 2. Leatherette/Orange Accent Grip stripe across middle
                                    drawRoundRect(
                                        color = gripColor.copy(alpha = 0.25f),
                                        topLeft = Offset(left, top + h * 0.25f),
                                        size = Size(w, h * 0.5f),
                                        cornerRadius = CornerRadius(2.dp.toPx())
                                    )

                                    // 3. Viewfinder top bump / prism
                                    val prismW = w * 0.36f
                                    val prismH = 3.5.dp.toPx()
                                    val prismLeft = left + w * 0.32f
                                    val prismTop = top - prismH
                                    val prismPath = Path().apply {
                                        moveTo(prismLeft, top)
                                        lineTo(prismLeft + 2.dp.toPx(), prismTop)
                                        lineTo(prismLeft + prismW - 2.dp.toPx(), prismTop)
                                        lineTo(prismLeft + prismW, top)
                                        close()
                                    }
                                    drawPath(prismPath, metalColor)
                                    drawPath(prismPath, darkOutline, style = Stroke(strokeW))

                                    // 4. Shutter Button (little knob on right)
                                    drawRoundRect(
                                        color = vibrantOrange,
                                        topLeft = Offset(left + w * 0.76f, top - 2.dp.toPx()),
                                        size = Size(w * 0.16f, 2.dp.toPx()),
                                        cornerRadius = CornerRadius(1.dp.toPx())
                                    )

                                    // 5. Camera body outline
                                    drawRoundRect(
                                        color = darkOutline,
                                        topLeft = Offset(left, top),
                                        size = Size(w, h),
                                        cornerRadius = CornerRadius(6.dp.toPx()),
                                        style = Stroke(strokeW)
                                    )

                                    // 6. Camera Lens Assembly (nested concentric circles with reflection)
                                    val lensCenter = Offset(left + w * 0.5f, top + h * 0.52f)
                                    val outerLensR = h * 0.38f
                                    val innerLensR = outerLensR * 0.65f
                                    val apertureR = innerLensR * 0.45f

                                    // Outer metallic bezel
                                    drawCircle(metalColor, radius = outerLensR, center = lensCenter)
                                    drawCircle(darkOutline, radius = outerLensR, center = lensCenter, style = Stroke(strokeW))

                                    // Inner lens glass
                                    drawCircle(darkOutline, radius = innerLensR, center = lensCenter)
                                    drawCircle(vibrantOrange, radius = apertureR, center = lensCenter)

                                    // Glass glint / reflection arc
                                    drawArc(
                                        color = Color.White.copy(alpha = 0.7f),
                                        startAngle = -135f,
                                        sweepAngle = 70f,
                                        useCenter = false,
                                        topLeft = Offset(lensCenter.x - innerLensR * 0.75f, lensCenter.y - innerLensR * 0.75f),
                                        size = Size(innerLensR * 1.5f, innerLensR * 1.5f),
                                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                                    )

                                    // Flash window in upper corner
                                    drawRoundRect(
                                        color = Color(0xFFFEF08A),
                                        topLeft = Offset(left + w * 0.12f, top + 2.5.dp.toPx()),
                                        size = Size(w * 0.14f, 3.dp.toPx()),
                                        cornerRadius = CornerRadius(1.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = darkOutline,
                                        topLeft = Offset(left + w * 0.12f, top + 2.5.dp.toPx()),
                                        size = Size(w * 0.14f, 3.dp.toPx()),
                                        cornerRadius = CornerRadius(1.dp.toPx()),
                                        style = Stroke(1.dp.toPx())
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Text running full width across card beneath header & icon
                    Text(
                        text = "Point camera at fridge shelves or counter\nto detect ingredients automatically",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = softGrayText
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onNavigateToScan,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = vibrantOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Open Scanner",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bento Split Row: 2 equal square cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bento Card 2: Manual Input
                Card(
                    onClick = onNavigateToManual,
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Pencil vector icon in circle
                        Surface(
                            shape = CircleShape,
                            color = warmTint,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val leadColor = darkPlum
                                    val woodColor = Color(0xFFFFD199)
                                    val bodyColor = vibrantOrange
                                    val eraserColor = Color(0xFFF472B6)
                                    val strokeW = 1.5.dp.toPx()

                                    val tip = Offset(3.5.dp.toPx(), size.height - 3.5.dp.toPx())
                                    val eraser = Offset(size.width - 4.5.dp.toPx(), 4.5.dp.toPx())
                                    val dx = (eraser.x - tip.x)
                                    val dy = (eraser.y - tip.y)
                                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                    val nx = -dy / dist
                                    val ny = dx / dist
                                    val halfW = 2.4.dp.toPx()

                                    // Main pencil shaft
                                    val shaftStartRatio = 0.28f
                                    val shaftEndRatio = 0.78f
                                    val pShaftStart = Offset(tip.x + dx * shaftStartRatio, tip.y + dy * shaftStartRatio)
                                    val pShaftEnd = Offset(tip.x + dx * shaftEndRatio, tip.y + dy * shaftEndRatio)

                                    val shaftPath = Path().apply {
                                        moveTo(pShaftStart.x + nx * halfW, pShaftStart.y + ny * halfW)
                                        lineTo(pShaftEnd.x + nx * halfW, pShaftEnd.y + ny * halfW)
                                        lineTo(pShaftEnd.x - nx * halfW, pShaftEnd.y - ny * halfW)
                                        lineTo(pShaftStart.x - nx * halfW, pShaftStart.y - ny * halfW)
                                        close()
                                    }
                                    drawPath(shaftPath, bodyColor)
                                    drawPath(shaftPath, darkPlum, style = Stroke(strokeW))

                                    // Wood cone
                                    val woodPath = Path().apply {
                                        moveTo(pShaftStart.x + nx * halfW, pShaftStart.y + ny * halfW)
                                        lineTo(tip.x, tip.y)
                                        lineTo(pShaftStart.x - nx * halfW, pShaftStart.y - ny * halfW)
                                        close()
                                    }
                                    drawPath(woodPath, woodColor)
                                    drawPath(woodPath, darkPlum, style = Stroke(strokeW))

                                    // Graphite tip
                                    val graphiteRatio = 0.12f
                                    val gEnd = Offset(tip.x + dx * graphiteRatio, tip.y + dy * graphiteRatio)
                                    val gHalfW = halfW * (graphiteRatio / shaftStartRatio)
                                    val graphitePath = Path().apply {
                                        moveTo(gEnd.x + nx * gHalfW, gEnd.y + ny * gHalfW)
                                        lineTo(tip.x, tip.y)
                                        lineTo(gEnd.x - nx * gHalfW, gEnd.y - ny * gHalfW)
                                        close()
                                    }
                                    drawPath(graphitePath, leadColor)

                                    // Ferrule metal band
                                    val ferruleEndRatio = 0.88f
                                    val pFerruleEnd = Offset(tip.x + dx * ferruleEndRatio, tip.y + dy * ferruleEndRatio)
                                    val ferrulePath = Path().apply {
                                        moveTo(pShaftEnd.x + nx * halfW, pShaftEnd.y + ny * halfW)
                                        lineTo(pFerruleEnd.x + nx * halfW, pFerruleEnd.y + ny * halfW)
                                        lineTo(pFerruleEnd.x - nx * halfW, pFerruleEnd.y - ny * halfW)
                                        lineTo(pShaftEnd.x - nx * halfW, pShaftEnd.y - ny * halfW)
                                        close()
                                    }
                                    drawPath(ferrulePath, Color(0xFFE2E8F0))
                                    drawPath(ferrulePath, darkPlum, style = Stroke(strokeW))

                                    // Eraser cap
                                    val eraserPath = Path().apply {
                                        moveTo(pFerruleEnd.x + nx * halfW, pFerruleEnd.y + ny * halfW)
                                        lineTo(eraser.x + nx * halfW, eraser.y + ny * halfW)
                                        lineTo(eraser.x - nx * halfW, eraser.y - ny * halfW)
                                        lineTo(pFerruleEnd.x - nx * halfW, pFerruleEnd.y - ny * halfW)
                                        close()
                                    }
                                    drawPath(eraserPath, eraserColor)
                                    drawPath(eraserPath, darkPlum, style = Stroke(strokeW))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Add Manually",
                            fontFamily = FontFamily.Serif,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkPlum
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Type ingredients",
                            fontSize = 12.sp,
                            color = softGrayText
                        )
                    }
                }

                // Bento Card 3: Default Pantry
                Card(
                    onClick = onOpenDefaultIngredients,
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Salt Shaker vector icon in circle
                        Surface(
                            shape = CircleShape,
                            color = mintGreen.copy(alpha = 0.15f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val strokeW = 1.6.dp.toPx()
                                    val capColor = Color(0xFF94A3B8)
                                    val glassOutline = darkPlum
                                    val glassFill = Color(0xFFF8FAFC)
                                    val saltColor = mintGreen

                                    val cx = size.width / 2f
                                    val topY = 2.5.dp.toPx()
                                    val capH = 4.dp.toPx()
                                    val capW = 7.dp.toPx()

                                    val neckY = topY + capH
                                    val neckW = 8.dp.toPx()

                                    val baseY = size.height - 2.5.dp.toPx()
                                    val baseW = 12.5.dp.toPx()

                                    // 1. Glass body path
                                    val bodyPath = Path().apply {
                                        moveTo(cx - neckW / 2f, neckY)
                                        lineTo(cx + neckW / 2f, neckY)
                                        lineTo(cx + baseW / 2f, baseY)
                                        lineTo(cx - baseW / 2f, baseY)
                                        close()
                                    }
                                    drawPath(bodyPath, glassFill)

                                    // 2. Salt fill inside bottom half
                                    val saltY = neckY + (baseY - neckY) * 0.45f
                                    val saltW = neckW + (baseW - neckW) * 0.45f
                                    val saltPath = Path().apply {
                                        moveTo(cx - saltW / 2f, saltY)
                                        lineTo(cx + saltW / 2f, saltY)
                                        lineTo(cx + baseW / 2f - 1.dp.toPx(), baseY - 1.dp.toPx())
                                        lineTo(cx - baseW / 2f + 1.dp.toPx(), baseY - 1.dp.toPx())
                                        close()
                                    }
                                    drawPath(saltPath, saltColor.copy(alpha = 0.35f))

                                    // 3. Shaker outline
                                    drawPath(bodyPath, glassOutline, style = Stroke(strokeW))

                                    // 4. Metal Cap
                                    val capPath = Path().apply {
                                        moveTo(cx - capW / 2f, neckY)
                                        lineTo(cx + capW / 2f, neckY)
                                        quadraticTo(cx, topY, cx - capW / 2f, neckY)
                                        close()
                                    }
                                    drawPath(capPath, capColor)
                                    drawPath(capPath, glassOutline, style = Stroke(strokeW))

                                    // 5. Perforation shaker holes on cap
                                    drawCircle(glassOutline, radius = 0.9.dp.toPx(), center = Offset(cx, neckY - 2.dp.toPx()))
                                    drawCircle(glassOutline, radius = 0.8.dp.toPx(), center = Offset(cx - 2.5.dp.toPx(), neckY - 1.6.dp.toPx()))
                                    drawCircle(glassOutline, radius = 0.8.dp.toPx(), center = Offset(cx + 2.5.dp.toPx(), neckY - 1.6.dp.toPx()))

                                    // 6. Vertical facet lines on glass
                                    val facetY1 = neckY + 2.dp.toPx()
                                    val facetY2 = baseY - 2.dp.toPx()
                                    drawLine(
                                        glassOutline.copy(alpha = 0.3f),
                                        Offset(cx - 2.2.dp.toPx(), facetY1),
                                        Offset(cx - 3.2.dp.toPx(), facetY2),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    drawLine(
                                        glassOutline.copy(alpha = 0.3f),
                                        Offset(cx + 2.2.dp.toPx(), facetY1),
                                        Offset(cx + 3.2.dp.toPx(), facetY2),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Pantry ($defaultIngredientsCount)",
                            fontFamily = FontFamily.Serif,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkPlum
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Assumed on hand",
                            fontSize = 12.sp,
                            color = softGrayText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
                        text = "Add Ingredients Manually",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Custom Mini Mascot Vector: Kawaii mini fridge peeking with a smile.
 */
@Composable
private fun MiniFridgeMascot(modifier: Modifier = Modifier) {
    val darkPlum = Color(0xFF2D143D)
    val vibrantOrange = Color(0xFFFF7A00)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val plum = darkPlum
        val orange = vibrantOrange
        val strokeW = 1.6.dp.toPx()

        // Fridge Body
        drawRoundRect(
            color = Color(0xFFF7F5F0),
            topLeft = Offset(w * 0.08f, h * 0.06f),
            size = Size(w * 0.84f, h * 0.82f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
        )
        drawRoundRect(
            color = plum,
            topLeft = Offset(w * 0.08f, h * 0.06f),
            size = Size(w * 0.84f, h * 0.82f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
            style = Stroke(width = strokeW)
        )

        // Freezer split line
        val splitY = h * 0.36f
        drawLine(
            color = plum,
            start = Offset(w * 0.08f, splitY),
            end = Offset(w * 0.92f, splitY),
            strokeWidth = strokeW
        )

        // Top Handle
        drawRoundRect(
            color = orange,
            topLeft = Offset(w * 0.16f, h * 0.16f),
            size = Size(w * 0.10f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
        // Bottom Handle
        drawRoundRect(
            color = orange,
            topLeft = Offset(w * 0.16f, h * 0.44f),
            size = Size(w * 0.10f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )

        // Happy Kawaii Face on lower door
        drawCircle(color = plum, radius = w * 0.045f, center = Offset(w * 0.46f, h * 0.54f))
        drawCircle(color = Color.White, radius = w * 0.018f, center = Offset(w * 0.45f, h * 0.53f))
        drawCircle(color = plum, radius = w * 0.045f, center = Offset(w * 0.74f, h * 0.54f))
        drawCircle(color = Color.White, radius = w * 0.018f, center = Offset(w * 0.73f, h * 0.53f))

        // Rosy Cheeks
        drawCircle(color = Color(0xFFFFB3B3), radius = w * 0.045f, center = Offset(w * 0.38f, h * 0.58f))
        drawCircle(color = Color(0xFFFFB3B3), radius = w * 0.045f, center = Offset(w * 0.82f, h * 0.58f))

        // Open Smile
        val mouthPath = Path().apply {
            moveTo(w * 0.53f, h * 0.57f)
            quadraticTo(w * 0.60f, h * 0.65f, w * 0.67f, h * 0.57f)
        }
        drawPath(mouthPath, color = plum, style = Stroke(width = strokeW * 0.9f, cap = StrokeCap.Round))

        // Little Strawberry Magnet on freezer
        drawCircle(color = Color(0xFFFF5252), radius = w * 0.06f, center = Offset(w * 0.72f, h * 0.22f))
        drawCircle(color = Color(0xFF4CAF50), radius = w * 0.025f, center = Offset(w * 0.72f, h * 0.16f))

        // Feet
        drawRoundRect(
            color = plum,
            topLeft = Offset(w * 0.22f, h * 0.88f),
            size = Size(w * 0.16f, h * 0.08f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
        drawRoundRect(
            color = plum,
            topLeft = Offset(w * 0.62f, h * 0.88f),
            size = Size(w * 0.16f, h * 0.08f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
    }
}

/**
 * Custom Salt Shaker Vector Icon
 */
@Composable
private fun MiniSaltShakerIcon(modifier: Modifier = Modifier) {
    val darkPlum = Color(0xFF2D143D)
    val mintGreen = Color(0xFF10B981)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = 1.5.dp.toPx()
        val plum = darkPlum
        val mint = mintGreen

        // Cap
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(w * 0.30f, h * 0.12f),
            size = Size(w * 0.40f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
        drawRoundRect(
            color = plum,
            topLeft = Offset(w * 0.30f, h * 0.12f),
            size = Size(w * 0.40f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(width = strokeW)
        )
        // Cap Holes
        drawCircle(plum, radius = strokeW * 0.7f, center = Offset(w * 0.42f, h * 0.22f))
        drawCircle(plum, radius = strokeW * 0.7f, center = Offset(w * 0.50f, h * 0.20f))
        drawCircle(plum, radius = strokeW * 0.7f, center = Offset(w * 0.58f, h * 0.22f))

        // Body
        val bodyPath = Path().apply {
            moveTo(w * 0.30f, h * 0.32f)
            lineTo(w * 0.20f, h * 0.85f)
            quadraticTo(w * 0.50f, h * 0.90f, w * 0.80f, h * 0.85f)
            lineTo(w * 0.70f, h * 0.32f)
            close()
        }
        drawPath(bodyPath, color = Color(0xFFF1FBF7))
        drawPath(bodyPath, color = plum, style = Stroke(width = strokeW))

        // Salt fill inside
        val saltPath = Path().apply {
            moveTo(w * 0.24f, h * 0.55f)
            quadraticTo(w * 0.50f, h * 0.50f, w * 0.76f, h * 0.55f)
            lineTo(w * 0.78f, h * 0.83f)
            quadraticTo(w * 0.50f, h * 0.88f, w * 0.22f, h * 0.83f)
            close()
        }
        drawPath(saltPath, color = mint.copy(alpha = 0.25f))

        // Little salt crystals
        drawCircle(mint, radius = strokeW * 0.6f, center = Offset(w * 0.44f, h * 0.68f))
        drawCircle(mint, radius = strokeW * 0.6f, center = Offset(w * 0.56f, h * 0.64f))
        drawCircle(mint, radius = strokeW * 0.6f, center = Offset(w * 0.50f, h * 0.75f))
    }
}

/**
 * Playful circular back button matching the warm theme.
 */
@Composable
private fun PlayfulBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val warmTint = Color(0xFFFFECD1)
    val vibrantOrange = Color(0xFFFF7A00)

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = warmTint,
        border = BorderStroke(1.dp, vibrantOrange.copy(alpha = 0.25f)),
        modifier = modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val strokeW = 2.4.dp.toPx()
                val arrowPath = Path().apply {
                    moveTo(size.width * 0.75f, size.height * 0.15f)
                    lineTo(size.width * 0.25f, size.height * 0.5f)
                    lineTo(size.width * 0.75f, size.height * 0.85f)
                }
                drawPath(arrowPath, color = vibrantOrange, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * Full page for reviewing and adding ingredients manually with Playful Bento theme.
 */
@Composable
fun IngredientSelectionPage(
    ingredients: List<String>,
    selected: Set<String>,
    defaultIngredientsCount: Int = 0,
    isSearching: Boolean = false,
    isScanResult: Boolean = false,
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

    val warmBg = Color(0xFFFFFBF0)         // Warm soft butter background
    val cardBg = Color.White
    val vibrantOrange = Color(0xFFFF7A00)  // Energetic warm orange
    val mintGreen = Color(0xFF10B981)      // Fresh kitchen mint
    val darkPlum = Color(0xFF2D143D)       // Rich deep plum text
    val warmTint = Color(0xFFFFECD1)       // Soft peach/butter tint
    val mintTint = Color(0xFFE8F8F2)       // Soft mint tint for selected items
    val borderColor = Color(0xFFF0E5D8)    // Subtle bento outline
    val mutedText = Color(0xFF8A7A8F)      // Soft plum gray

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(warmBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Header Bar: Back button, Title & subtitle, Clear All action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayfulBackButton(onClick = onBack)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Add Ingredients",
                            fontFamily = FontFamily.Cursive,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = vibrantOrange
                        )
                        Text(
                            text = if (ingredients.isNotEmpty()) {
                                "${ingredients.size} item${if (ingredients.size == 1) "" else "s"} added to your kitchen"
                            } else {
                                "Type what you have in your kitchen"
                            },
                            fontSize = 12.sp,
                            color = mutedText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (ingredients.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = warmTint,
                        modifier = Modifier.clickable { onClearAll() }
                    ) {
                        Text(
                            text = "Clear All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = vibrantOrange,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // AI Scan Success Celebration Banner (Shown when coming from a scan with items)
            if (isScanResult && ingredients.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.2.dp, borderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniFridgeMascot(modifier = Modifier.size(46.dp, 56.dp))

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = mintTint
                                ) {
                                    Text(
                                        text = "SCAN SUCCESS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = mintGreen,
                                        letterSpacing = 0.6f.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "✨ ${ingredients.size} detected",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = darkPlum
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "We spotted these ingredients! Tap to include or exclude items.",
                                fontSize = 12.sp,
                                color = darkPlum.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Pantry Staples Pill Bar (Single-line sleek pill)
            if (onOpenDefaultIngredients != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.2.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDefaultIngredients() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MiniSaltShakerIcon(modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pantry Staples ($defaultIngredientsCount active)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = darkPlum
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = mintTint
                        ) {
                            Text(
                                text = "Edit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = mintGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Add Ingredients Input Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.2.dp, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Add Ingredients",
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkPlum
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualText,
                            onValueChange = { manualText = it },
                            placeholder = {
                                Text(
                                    text = "e.g. onion, butter, rice...",
                                    fontSize = 13.sp,
                                    color = mutedText
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = warmBg,
                                unfocusedContainerColor = warmBg,
                                focusedBorderColor = vibrantOrange,
                                unfocusedBorderColor = borderColor,
                                focusedTextColor = darkPlum,
                                unfocusedTextColor = darkPlum
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { handleAdd() }),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = handleAdd,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = vibrantOrange),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text(
                                text = "+ Add",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add one by one, or separate multiple by ,",
                        fontSize = 11.sp,
                        color = mutedText,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Ingredients Content: Empty Basket Card OR Chunky Bento Chips
            if (ingredients.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.2.dp, borderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MiniFridgeMascot(modifier = Modifier.size(68.dp, 80.dp))

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Your basket is empty!",
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkPlum
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Type the ingredients you have available above and tap + Add to discover matching recipes.",
                            fontSize = 12.sp,
                            color = mutedText,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected (${selected.size} of ${ingredients.size})",
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkPlum
                    )
                    Text(
                        text = "Tap to toggle",
                        fontSize = 11.sp,
                        color = mutedText,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ingredients.forEach { item ->
                        val isSelected = selected.contains(item)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) mintTint else cardBg,
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) mintGreen else borderColor
                            ),
                            modifier = Modifier.clickable {
                                if (isSelected) onSelectionChange(selected - item)
                                else onSelectionChange(selected + item)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) mintGreen else warmTint,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isSelected) {
                                            Text(
                                                text = "✓",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        } else {
                                            Text(
                                                text = "+",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = darkPlum
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = item,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) darkPlum else mutedText
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onRemoveIngredient(item) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "✕",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = mutedText.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Floating Sticky Bottom Bar
        Surface(
            shadowElevation = 12.dp,
            color = cardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (selected.isNotEmpty()) {
                    Button(
                        onClick = onSearch,
                        enabled = !isSearching,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = vibrantOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Finding Recipes...",
                                fontFamily = FontFamily.Serif,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Find Recipes with ${selected.size} Item${if (selected.size == 1) "" else "s"}",
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "→",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF1EBE2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Add ingredients to search",
                                fontFamily = FontFamily.Serif,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = mutedText
                            )
                        }
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
