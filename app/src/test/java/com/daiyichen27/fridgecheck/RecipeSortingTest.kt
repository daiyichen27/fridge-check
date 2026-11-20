package com.daiyichen27.fridgecheck

import com.example.fridgecheck.data.MealDetail
import com.example.fridgecheck.data.MealSummary
import com.example.fridgecheck.data.Recipe
import com.example.fridgecheck.data.countMissingIngredients
import com.example.fridgecheck.data.findOverlappingMeals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSortingTest {

    private fun countMissingIngredientsMock(recipeIngredients: List<String>, owned: List<String>): Int {
        val ownedLower = owned.map { it.lowercase().trim() }
        return recipeIngredients.count { ingredient ->
            ownedLower.none { it in ingredient.lowercase() }
        }
    }

    @Test
    fun `sorting prioritized recipes with fewer missing ingredients`() {
        val availableIngredients = listOf("Chicken", "Onion")

        val perfectMatch = listOf("1lb Chicken", "1 sliced Onion") // 0 missing
        val partialMatch = listOf("Chicken", "Onion", "Curry Paste", "Coconut Milk") // 2 missing

        val missingForPerfect = countMissingIngredientsMock(perfectMatch, availableIngredients)
        val missingForPartial = countMissingIngredientsMock(partialMatch, availableIngredients)

        // Assertions
        assertEquals(0, missingForPerfect)
        assertEquals(2, missingForPartial)
    }

    @Test
    fun `countMissingIngredients works with domain Recipe model`() {
        val recipe = Recipe(
            label = "Chicken Soup",
            image = "",
            url = "",
            ingredientLines = listOf("1 lb Chicken", "1 sliced Onion", "2 carrots")
        )
        val owned = setOf("Chicken", "Onion")
        val missing = countMissingIngredients(recipe, owned)
        assertEquals(1, missing) // carrots is missing
    }

    @Test
    fun `findOverlappingMeals returns all meals when single ingredient queried`() {
        val meals = listOf(
            MealSummary(idMeal = "1", strMeal = "Chicken Curry", strMealThumb = null),
            MealSummary(idMeal = "2", strMeal = "Chicken Soup", strMealThumb = null)
        )
        val result = findOverlappingMeals(listOf(meals))
        assertEquals(2, result.size)
        assertEquals(listOf("1", "2"), result.map { it.idMeal })
    }

    @Test
    fun `findOverlappingMeals returns intersecting meals across two ingredient queries`() {
        val chickenMeals = listOf(
            MealSummary(idMeal = "1", strMeal = "Chicken Curry", strMealThumb = null),
            MealSummary(idMeal = "2", strMeal = "Chicken Rice", strMealThumb = null),
            MealSummary(idMeal = "3", strMeal = "Chicken Soup", strMealThumb = null)
        )
        val riceMeals = listOf(
            MealSummary(idMeal = "2", strMeal = "Chicken Rice", strMealThumb = null),
            MealSummary(idMeal = "4", strMeal = "Fried Rice", strMealThumb = null)
        )

        val result = findOverlappingMeals(listOf(chickenMeals, riceMeals))
        assertEquals(1, result.size)
        assertEquals("2", result.first().idMeal)
        assertEquals("Chicken Rice", result.first().strMeal)
    }

    @Test
    fun `findOverlappingMeals returns empty list when no recipes overlap`() {
        val beefMeals = listOf(
            MealSummary(idMeal = "10", strMeal = "Beef Steak", strMealThumb = null)
        )
        val chocolateMeals = listOf(
            MealSummary(idMeal = "20", strMeal = "Chocolate Cake", strMealThumb = null)
        )

        val result = findOverlappingMeals(listOf(beefMeals, chocolateMeals))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findOverlappingMeals handles empty and empty-query lists gracefully`() {
        val emptyResult = findOverlappingMeals(emptyList())
        assertTrue(emptyResult.isEmpty())

        val meals = listOf(
            MealSummary(idMeal = "1", strMeal = "Meal 1", strMealThumb = null)
        )
        // One query returned empty, one returned meals
        val mixedResult = findOverlappingMeals(listOf(emptyList(), meals))
        assertEquals(1, mixedResult.size)
        assertEquals("1", mixedResult.first().idMeal)
    }

    @Test
    fun `findOverlappingMeals prioritizes full matches across 3 ingredients`() {
        val q1 = listOf(
            MealSummary(idMeal = "1", strMeal = "All Three Match", strMealThumb = null),
            MealSummary(idMeal = "2", strMeal = "Matches Q1 and Q2", strMealThumb = null)
        )
        val q2 = listOf(
            MealSummary(idMeal = "1", strMeal = "All Three Match", strMealThumb = null),
            MealSummary(idMeal = "2", strMeal = "Matches Q1 and Q2", strMealThumb = null)
        )
        val q3 = listOf(
            MealSummary(idMeal = "1", strMeal = "All Three Match", strMealThumb = null)
        )

        val result = findOverlappingMeals(listOf(q1, q2, q3))
        assertEquals(1, result.size)
        assertEquals("1", result.first().idMeal)
    }

    @Test
    fun `MealDetail toIngredientLines correctly formats ingredients and measures`() {
        val detail = MealDetail(
            idMeal = "53016",
            strMeal = "Chick-Fil-A Sandwich",
            strIngredient1 = "Chicken Breast",
            strMeasure1 = "1",
            strIngredient2 = "Pickle Juice",
            strMeasure2 = "1/4 cup",
            strIngredient3 = "Egg",
            strMeasure3 = "",
            strIngredient4 = "",
            strMeasure4 = ""
        )

        val lines = detail.toIngredientLines()
        assertEquals(3, lines.size)
        assertEquals("1 Chicken Breast", lines[0])
        assertEquals("1/4 cup Pickle Juice", lines[1])
        assertEquals("Egg", lines[2])
    }
}
