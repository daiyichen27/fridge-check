package com.daiyichen27.fridgecheck

import org.junit.Assert
import org.junit.Test

class RecipeSortingTest {

    // Mocking your logic function
    // (If your actual function is in MainActivity, you may need to make it 'internal')
    private fun countMissingIngredients(recipeIngredients: List<String>, owned: List<String>): Int {
        val ownedLower = owned.map { it.lowercase().trim() }
        return recipeIngredients.count { ingredient ->
            ownedLower.none { it in ingredient.lowercase() }
        }
    }

    @Test
    fun `sorting prioritized recipes with fewer missing ingredients`() {
        val myFridge = listOf("Chicken", "Onion")

        val perfectMatch = listOf("1lb Chicken", "1 sliced Onion") // 0 missing
        val partialMatch = listOf("Chicken", "Onion", "Curry Paste", "Coconut Milk") // 2 missing

        val missingForPerfect = countMissingIngredients(perfectMatch, myFridge)
        val missingForPartial = countMissingIngredients(partialMatch, myFridge)

        // Assertions
        Assert.assertEquals(0, missingForPerfect)
        Assert.assertEquals(2, missingForPartial)
    }
}