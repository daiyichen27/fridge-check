package com.example.fridgecheck.data

/**
 * Finds meal summaries matching the given ingredient query results, prioritized by overlap.
 *
 * - If [queryResults] is empty or all results are empty, returns an empty list.
 * - If only 1 ingredient query result is provided, returns all distinct meals from that query.
 * - If multiple ingredient queries are provided, returns all distinct recipes that appear
 *   in one or more of the ingredient queries:
 *   - Ranked primarily by the number of matching ingredient queries (highest overlap first).
 *   - Ranked secondarily alphabetically by meal name ([MealSummary.strMeal]).
 *
 * This ensures recipes that include all or most ingredients appear at the top, while recipes
 * that contain at least some of the ingredients are still returned rather than returning an empty list.
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

    // Sort by match count descending (highest overlap first), then alphabetically by name
    return occurrenceCounts.toList()
        .sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy { allMealsById[it.first]?.strMeal.orEmpty() }
        )
        .mapNotNull { allMealsById[it.first] }
}

fun countMissingIngredients(recipe: Recipe, ownedIngredients: Set<String>): Int {
    var matches = 0

    // Check each line of the recipe (e.g., "1lb Chicken")
    // against each thing we have (e.g., "Chicken")
    recipe.ingredientLines.forEach { line ->
        val foundMatch = ownedIngredients.any { owned ->
            line.contains(owned, ignoreCase = true)
        }
        if (foundMatch) matches++
    }

    // Missing = Total required - how many we matched
    val missing = recipe.ingredientLines.size - matches
    return if (missing < 0) 0 else missing
}
