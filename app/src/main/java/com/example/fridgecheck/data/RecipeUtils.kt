package com.example.fridgecheck.data

/**
 * Finds meal summaries that overlap across multiple ingredient query results.
 *
 * - If [queryResults] is empty or all results are empty, returns an empty list.
 * - If only 1 ingredient query result is provided, returns all distinct meals from that query.
 * - If multiple ingredient queries are provided, returns recipes that overlap across the queries:
 *   - If there are recipes present across all non-empty queries (full intersection),
 *     those are returned.
 *   - If no single recipe matches all queries, returns recipes that overlap across at least 2 queries,
 *     ordered by the number of matching queries descending.
 */
fun findOverlappingMeals(queryResults: List<List<MealSummary>>): List<MealSummary> {
    val validResults = queryResults.filter { it.isNotEmpty() }
    if (validResults.isEmpty()) return emptyList()
    if (validResults.size == 1) return validResults[0].distinctBy { it.idMeal }

    val allMealsById = validResults.flatten().associateBy { it.idMeal }
    val idSets = validResults.map { list -> list.map { it.idMeal }.toSet() }

    // Check for full intersection across all non-empty ingredient queries
    val fullIntersectionIds = idSets.reduce { acc, set -> acc.intersect(set) }
    if (fullIntersectionIds.isNotEmpty()) {
        return fullIntersectionIds.mapNotNull { allMealsById[it] }
            .sortedBy { it.strMeal }
    }

    // Fallback: partial overlap (matching at least 2 queries), sorted by match count descending
    val occurrenceCounts = mutableMapOf<String, Int>()
    for (set in idSets) {
        for (id in set) {
            occurrenceCounts[id] = (occurrenceCounts[id] ?: 0) + 1
        }
    }

    return occurrenceCounts.filter { it.value >= 2 }
        .toList()
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
