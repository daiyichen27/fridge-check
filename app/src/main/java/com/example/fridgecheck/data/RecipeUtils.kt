package com.example.fridgecheck.data

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