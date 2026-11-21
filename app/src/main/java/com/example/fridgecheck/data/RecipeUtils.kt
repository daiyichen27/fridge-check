package com.example.fridgecheck.data

/**
 * Cleans user-entered ingredient strings by stripping leading conjunctions or filler words.
 * (e.g. "and noodles" -> "noodles", "& spring onions" -> "spring onions")
 */
fun cleanInputIngredient(raw: String): String {
    var cleaned = raw.trim()
    val prefixes = listOf("and ", "& ", "with ", "or ", "some ", "a ", "an ")
    for (prefix in prefixes) {
        if (cleaned.startsWith(prefix, ignoreCase = true)) {
            cleaned = cleaned.substring(prefix.length).trim()
        }
    }
    return cleaned
}

/**
 * Returns prioritized query string candidates for TheMealDB API search.
 * TheMealDB uses strict string matching and has inconsistent singular/plural naming
 * (e.g. requires "chicken_thighs" plural, "mushrooms" plural, but "onion" / "onions", "chicken" base).
 */
fun getIngredientSearchCandidates(rawIngredient: String): List<String> {
    val cleaned = cleanInputIngredient(rawIngredient).lowercase()
    if (cleaned.isEmpty()) return emptyList()

    val candidates = mutableListOf<String>()
    fun addCandidate(term: String) {
        val formatted = term.trim().replace(" ", "_")
        if (formatted.isNotEmpty() && !candidates.contains(formatted)) {
            candidates.add(formatted)
        }
    }

    // 1. Direct formatted query
    addCandidate(cleaned)

    // 2. Singular / Plural variation
    if (cleaned.endsWith("s")) {
        // Try singular
        if (cleaned.endsWith("es") && cleaned.length > 3) {
            addCandidate(cleaned.removeSuffix("es"))
        }
        addCandidate(cleaned.removeSuffix("s"))
    } else {
        // Try plural
        addCandidate("${cleaned}s")
        addCandidate("${cleaned}es")
    }

    // 3. Common ingredient synonyms & TheMealDB naming quirks
    when {
        cleaned.contains("scallion") || cleaned.contains("green onion") -> {
            addCandidate("spring_onions")
        }
        cleaned.contains("spring onion") -> {
            addCandidate("spring_onions")
        }
        cleaned.contains("ground beef") || cleaned.contains("beef mince") -> {
            addCandidate("minced_beef")
            addCandidate("beef")
        }
        cleaned.contains("ground pork") || cleaned.contains("pork mince") -> {
            addCandidate("pork")
        }
        cleaned.contains("ground chicken") || cleaned.contains("chicken mince") -> {
            addCandidate("ground_chicken")
            addCandidate("chicken")
        }
        cleaned.contains("cilantro") -> {
            addCandidate("coriander")
        }
        cleaned.contains("aubergine") -> {
            addCandidate("eggplant")
        }
        cleaned.contains("courgette") -> {
            addCandidate("zucchini")
        }
        cleaned.contains("bell pepper") || cleaned.contains("capsicum") -> {
            addCandidate("green_pepper")
            addCandidate("red_pepper")
            addCandidate("pepper")
        }
        cleaned.contains("chili") || cleaned.contains("chilli") -> {
            addCandidate("red_chilli")
            addCandidate("chilli")
        }
        cleaned.contains("ramen") || cleaned.contains("udon") || cleaned.contains("soba") -> {
            addCandidate("udon_noodles")
            addCandidate("noodles")
        }
        cleaned.contains("pasta") || cleaned.contains("spaghetti") -> {
            addCandidate("noodles")
        }
    }

    // 4. Base protein / parent ingredient fallback
    if (cleaned.contains("chicken") && cleaned != "chicken") {
        addCandidate("chicken")
    }
    if (cleaned.contains("beef") && cleaned != "beef") {
        addCandidate("beef")
    }
    if (cleaned.contains("pork") && cleaned != "pork") {
        addCandidate("pork")
    }
    if (cleaned.contains("salmon") && cleaned != "salmon") {
        addCandidate("salmon")
    }
    if ((cleaned.contains("shrimp") || cleaned.contains("prawn")) && !cleaned.contains("prawns")) {
        addCandidate("prawns")
        addCandidate("king_prawns")
    }
    if (cleaned.contains("lamb") && cleaned != "lamb") {
        addCandidate("lamb")
    }
    if (cleaned.contains("noodle") && cleaned != "noodles") {
        addCandidate("noodles")
    }

    return candidates
}

val MAIN_COURSE_CATEGORIES = setOf(
    "chicken",
    "beef",
    "pork",
    "seafood",
    "lamb",
    "goat",
    "pasta"
)

/**
 * Finds meal summaries matching the given ingredient query results, prioritized by overlap.
 *
 * - If [queryResults] is empty or all results are empty, returns an empty list.
 * - If only 1 ingredient query result is provided, returns all distinct meals from that query.
 * - If multiple ingredient queries are provided:
 *   - Recipes overlapping across more queries appear first (e.g. 3 matches, then 2 matches).
 *   - Single-query matches (count = 1) are interleaved evenly across the queries so no single
 *     ingredient dominates the candidate lookup pool.
 */
fun findOverlappingMeals(queryResults: List<List<MealSummary>>): List<MealSummary> {
    val validResults = queryResults.filter { it.isNotEmpty() }
    if (validResults.isEmpty()) return emptyList()
    if (validResults.size == 1) return validResults[0].distinctBy { it.idMeal }

    val allMealsById = validResults.flatten().associateBy { it.idMeal }
    val idSets = validResults.map { list -> list.map { it.idMeal }.toSet() }

    // Count how many ingredient query results each meal appears in
    val occurrenceCounts = mutableMapOf<String, Int>()
    for (set in idSets) {
        for (id in set) {
            occurrenceCounts[id] = (occurrenceCounts[id] ?: 0) + 1
        }
    }

    val maxCount = occurrenceCounts.values.maxOrNull() ?: 1
    val result = mutableListOf<MealSummary>()
    val addedIds = mutableSetOf<String>()

    // 1. Add meals that overlap across multiple queries (from the highest overlap down to 2)
    for (count in maxCount downTo 2) {
        val mealsAtCount = occurrenceCounts.filter { it.value == count }
            .keys
            .sortedBy { allMealsById[it]?.strMeal.orEmpty() }
        for (id in mealsAtCount) {
            if (addedIds.add(id)) {
                allMealsById[id]?.let { result.add(it) }
            }
        }
    }

    // 2. For single-query meals (count = 1), interleave across the queried ingredients
    // so taking the top candidates provides a balanced representation across ingredients
    val singleMatchLists = validResults.map { queryList ->
        queryList.filter { occurrenceCounts[it.idMeal] == 1 }.distinctBy { it.idMeal }
    }

    val maxSingleListSize = singleMatchLists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until maxSingleListSize) {
        for (list in singleMatchLists) {
            if (i < list.size) {
                val meal = list[i]
                if (addedIds.add(meal.idMeal)) {
                    result.add(meal)
                }
            }
        }
    }

    return result
}

enum class ProteinGroup(val categoryName: String, val keywords: Set<String>) {
    CHICKEN("chicken", setOf("chicken", "turkey", "poultry", "duck")),
    SEAFOOD("seafood", setOf(
        "salmon", "fish", "shrimp", "prawn", "prawns", "tuna", "cod",
        "trout", "halibut", "crab", "lobster", "calamari", "squid",
        "mussel", "mussels", "scallop", "scallops", "tilapia", "haddock",
        "anchovy", "anchovies", "sardine", "sardines"
    )),
    BEEF("beef", setOf("beef", "steak", "ground beef", "mince", "sirloin", "brisket", "chuck", "ribeye", "veal")),
    PORK("pork", setOf("pork", "bacon", "ham", "sausage", "prosciutto", "pancetta", "pork chop", "pork loin")),
    LAMB("lamb", setOf("lamb", "mutton")),
    GOAT("goat", setOf("goat"))
}

fun detectUserProteinGroups(userIngredients: Set<String>): Set<ProteinGroup> {
    val lowerIngredients = userIngredients.map { it.lowercase().trim() }
    return ProteinGroup.entries.filter { group ->
        lowerIngredients.any { ingredient ->
            group.keywords.any { keyword -> ingredient.contains(keyword) }
        }
    }.toSet()
}

/**
 * Enforces protein consistency:
 * If the user provided one or more major proteins (e.g. Chicken, Seafood/Salmon):
 * 1. Rejects recipes whose category belongs to a competing, unowned protein (e.g. Beef, Lamb, Pork).
 * 2. Requires the recipe to actually use at least one of the user's provided proteins.
 */
fun shouldKeepRecipeForProteins(recipe: Recipe, userIngredients: Set<String>): Boolean {
    val userProteins = detectUserProteinGroups(userIngredients)
    if (userProteins.isEmpty()) {
        // User didn't specify any major protein, so keep all matching recipes
        return true
    }

    val recipeCategoryLower = recipe.category.trim().lowercase()
    val userProteinCategories = userProteins.map { it.categoryName }.toSet()
    val allProteinCategories = ProteinGroup.entries.map { it.categoryName }.toSet()

    // 1. If the recipe is categorized under a competing unowned protein, reject it
    if (recipeCategoryLower in allProteinCategories && recipeCategoryLower !in userProteinCategories) {
        return false
    }

    // 2. Ensure the recipe uses at least one of the user's provided proteins
    val allUserProteinKeywords = userProteins.flatMap { it.keywords }
    val containsUserProtein = recipe.ingredientLines.any { line ->
        allUserProteinKeywords.any { keyword -> line.contains(keyword, ignoreCase = true) }
    } || userProteinCategories.contains(recipeCategoryLower)

    return containsUserProtein
}

val TRIVIAL_GARNISH_KEYWORDS = setOf(
    "parsley",
    "cilantro",
    "coriander",
    "chives",
    "sesame seed",
    "sesame seeds",
    "mint leaves",
    "fresh mint",
    "bay leaf",
    "bay leaves",
    "lemon wedge",
    "lime wedge",
    "lemon slice",
    "lime slice",
    "garnish"
)

val TRIVIAL_MEASURE_KEYWORDS = setOf(
    "garnish",
    "to garnish",
    "for garnish",
    "to serve",
    "for serving",
    "optional",
    "pinch",
    "a pinch",
    "sprig",
    "sprigs"
)

fun isTrivialOrGarnish(ingredientLine: String): Boolean {
    val lineLower = ingredientLine.lowercase().trim()
    if (TRIVIAL_MEASURE_KEYWORDS.any { lineLower.contains(it) }) return true
    if (TRIVIAL_GARNISH_KEYWORDS.any { lineLower.contains(it) }) return true
    return false
}

/**
 * Calculates a multifactor score for a recipe to prioritize:
 * 1. Recipes matching more user-scanned ingredients (+50 points per scanned ingredient matched).
 * 2. Main course categories (+20 points for hearty dinner categories like Chicken, Beef, Pork, Seafood, etc.).
 * 3. Recipes where assumed default ingredients fill remaining gaps (+2 points per default ingredient).
 * 4. High ingredient coverage / completeness (+10 * (matched / total)).
 * 5. Penalizing missing ingredients (-3 points per missing ingredient, excluding trivial garnishes).
 */
fun calculateRecipeScore(
    recipe: Recipe,
    scannedIngredients: Set<String>,
    defaultIngredients: Set<String> = emptySet()
): Double {
    if (recipe.ingredientLines.isEmpty()) return 0.0

    val cleanScanned = scannedIngredients.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    val cleanDefaults = defaultIngredients.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    // Count how many distinct scanned items are matched in this recipe
    val matchedScannedCount = cleanScanned.count { scanned ->
        recipe.ingredientLines.any { line -> line.contains(scanned, ignoreCase = true) }
    }

    // Count default ingredients matched vs essential missing ingredients (ignoring trivial garnishes)
    var matchedDefaultCount = 0
    var missingCount = 0

    recipe.ingredientLines.forEach { line ->
        val lineLower = line.lowercase()
        val matchesScanned = cleanScanned.any { it in lineLower }
        if (!matchesScanned) {
            val matchesDefault = cleanDefaults.any { it in lineLower }
            if (matchesDefault) {
                matchedDefaultCount++
            } else if (!isTrivialOrGarnish(line)) {
                missingCount++
            }
        }
    }

    val essentialCount = recipe.ingredientLines.count { !isTrivialOrGarnish(it) }.coerceAtLeast(1)
    val totalMatched = (essentialCount - missingCount).coerceAtLeast(0)
    val coverageRatio = totalMatched.toDouble() / essentialCount

    // Main course category boost
    val isMainCourse = recipe.category.trim().lowercase() in MAIN_COURSE_CATEGORIES
    val categoryBoost = if (isMainCourse) 20.0 else 0.0

    return ((matchedScannedCount * 50.0) +
            categoryBoost +
            (matchedDefaultCount * 2.0) +
            (coverageRatio * 10.0)) -
            (missingCount * 3.0)
}

fun countMissingIngredients(recipe: Recipe, ownedIngredients: Set<String>): Int {
    val cleanOwned = ownedIngredients.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    return recipe.ingredientLines.count { line ->
        val lineLower = line.lowercase()
        val isOwned = cleanOwned.any { it in lineLower }

        // An ingredient line is missing ONLY if:
        // 1. It is not owned, AND
        // 2. It is NOT a trivial ingredient or garnish
        !isOwned && !isTrivialOrGarnish(line)
    }
}
