package com.daiyichen27.fridgecheck

import com.example.fridgecheck.data.MealDetail
import com.example.fridgecheck.data.MealSummary
import com.example.fridgecheck.data.Recipe
import com.example.fridgecheck.data.calculateRecipeScore
import com.example.fridgecheck.data.countMissingIngredients
import com.example.fridgecheck.data.findOverlappingMeals
import com.example.fridgecheck.data.isTrivialOrGarnish
import com.example.fridgecheck.data.shouldKeepRecipeForProteins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `findOverlappingMeals returns intersecting meals first followed by partial matches`() {
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
        // Chicken Rice overlaps both queries, so it's first
        assertEquals(4, result.size)
        assertEquals("2", result.first().idMeal)
        assertEquals("Chicken Rice", result.first().strMeal)
        // Other recipes containing at least one ingredient are still included
        assertTrue(result.any { it.idMeal == "1" })
        assertTrue(result.any { it.idMeal == "4" })
    }

    @Test
    fun `findOverlappingMeals returns recipes containing some ingredients when no recipes overlap all ingredients`() {
        val beefMeals = listOf(
            MealSummary(idMeal = "10", strMeal = "Beef Steak", strMealThumb = null)
        )
        val chocolateMeals = listOf(
            MealSummary(idMeal = "20", strMeal = "Chocolate Cake", strMealThumb = null)
        )

        val result = findOverlappingMeals(listOf(beefMeals, chocolateMeals))
        // Rather than returning empty, it returns the meals containing some of the ingredients
        assertEquals(2, result.size)
        assertEquals(listOf("10", "20"), result.map { it.idMeal })
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
    fun `findOverlappingMeals prioritizes full matches across 3 ingredients followed by partial matches`() {
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
        assertEquals(2, result.size)
        assertEquals("1", result[0].idMeal)
        assertEquals("2", result[1].idMeal)
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

    @Test
    fun `default ingredients are counted as owned in missing ingredient calculation`() {
        val recipe = Recipe(
            label = "Seasoned Grilled Chicken",
            image = "",
            url = "",
            ingredientLines = listOf("500g Chicken Breast", "1 tsp Salt", "Pinch of Black Pepper", "2 tbsp Olive Oil")
        )

        // User only scanned/selected chicken
        val scannedIngredients = setOf("Chicken")
        // User has common default ingredients assumed
        val defaultIngredients = setOf("Salt", "Pepper", "Olive Oil")

        val allOwned = scannedIngredients + defaultIngredients
        val missingCount = countMissingIngredients(recipe, allOwned)

        assertEquals(0, missingCount)
    }

    @Test
    fun `calculateRecipeScore boosts recipes matching more scanned ingredients`() {
        val scanned = setOf("Chicken", "Cucumber")
        val defaults = setOf("Salt", "Pepper", "Oil")

        val recipeMatchesBoth = Recipe(
            label = "Chicken and Cucumber Stir Fry",
            image = "",
            url = "",
            category = "Chicken",
            ingredientLines = listOf("Chicken Thigh", "Sliced Cucumber", "Salt", "Soy Sauce", "Cornstarch")
        )

        val recipeMatchesOne = Recipe(
            label = "Cucumber Salad",
            image = "",
            url = "",
            category = "Side",
            ingredientLines = listOf("Cucumber", "Salt", "Vinegar")
        )

        val scoreBoth = calculateRecipeScore(recipeMatchesBoth, scanned, defaults)
        val scoreOne = calculateRecipeScore(recipeMatchesOne, scanned, defaults)

        assertTrue(
            "Recipe matching both items ($scoreBoth) should score higher than matching only one ($scoreOne)",
            scoreBoth > scoreOne
        )
    }

    @Test
    fun `calculateRecipeScore boosts main course categories over simple side dishes`() {
        val scanned = setOf("Chicken Thigh", "Cucumber")
        val defaults = setOf("Salt", "Pepper", "Olive Oil", "Garlic")

        // A chicken entree with multiple ingredients (e.g. 7 ingredients, 2 missing)
        val chickenEntree = Recipe(
            label = "Garlic Herb Roasted Chicken Thighs",
            image = "",
            url = "",
            category = "Chicken",
            ingredientLines = listOf(
                "4 Chicken Thighs",
                "3 cloves Garlic",
                "2 tbsp Olive Oil",
                "1 tsp Salt",
                "1/2 tsp Black Pepper",
                "1 sprig Rosemary",
                "1 tsp Paprika"
            )
        )

        // A simple cucumber side dish (3 ingredients, 0 missing because of defaults)
        val cucumberSide = Recipe(
            label = "Quick Cucumber Salad",
            image = "",
            url = "",
            category = "Side",
            ingredientLines = listOf(
                "1 Cucumber",
                "1/2 tsp Salt",
                "1 tbsp Olive Oil"
            )
        )

        val chickenScore = calculateRecipeScore(chickenEntree, scanned, defaults)
        val cucumberScore = calculateRecipeScore(cucumberSide, scanned, defaults)

        assertTrue(
            "Chicken entree ($chickenScore) should outrank simple cucumber side dish ($cucumberScore)",
            chickenScore > cucumberScore
        )
    }

    @Test
    fun `findOverlappingMeals interleaves single query results across ingredients`() {
        // Query 1 has 3 chicken meals
        val chickenMeals = listOf(
            MealSummary(idMeal = "c1", strMeal = "Chicken Curry", strMealThumb = null),
            MealSummary(idMeal = "c2", strMeal = "Chicken Roast", strMealThumb = null),
            MealSummary(idMeal = "c3", strMeal = "Chicken Soup", strMealThumb = null)
        )
        // Query 2 has 2 cucumber meals
        val cucumberMeals = listOf(
            MealSummary(idMeal = "cu1", strMeal = "Cucumber Salad", strMealThumb = null),
            MealSummary(idMeal = "cu2", strMeal = "Pickled Cucumber", strMealThumb = null)
        )

        val result = findOverlappingMeals(listOf(chickenMeals, cucumberMeals))

        assertEquals(5, result.size)
        // Should interleave: chicken 1, cucumber 1, chicken 2, cucumber 2, chicken 3
        assertEquals("c1", result[0].idMeal)
        assertEquals("cu1", result[1].idMeal)
        assertEquals("c2", result[2].idMeal)
        assertEquals("cu2", result[3].idMeal)
        assertEquals("c3", result[4].idMeal)
    }

    @Test
    fun `shouldKeepRecipeForProteins filters out beef, lamb, and pork when user has chicken thigh and salmon`() {
        val userIngredients = setOf("Chicken Thigh", "Salmon", "Onion")

        val beefRecipe = Recipe(
            label = "Beef Bourguignon",
            image = "",
            url = "",
            category = "Beef",
            ingredientLines = listOf("Beef Chuck", "Onion", "Red Wine", "Carrots")
        )

        val lambRecipe = Recipe(
            label = "Lamb Rogan Josh",
            image = "",
            url = "",
            category = "Lamb",
            ingredientLines = listOf("Lamb Shank", "Onion", "Curry Powder")
        )

        val porkRecipe = Recipe(
            label = "Pork Schnitzel",
            image = "",
            url = "",
            category = "Pork",
            ingredientLines = listOf("Pork Cutlet", "Breadcrumbs", "Egg")
        )

        assertFalse(shouldKeepRecipeForProteins(beefRecipe, userIngredients))
        assertFalse(shouldKeepRecipeForProteins(lambRecipe, userIngredients))
        assertFalse(shouldKeepRecipeForProteins(porkRecipe, userIngredients))
    }

    @Test
    fun `shouldKeepRecipeForProteins keeps chicken and seafood recipes when user has chicken thigh and salmon`() {
        val userIngredients = setOf("Chicken Thigh", "Salmon", "Onion")

        val chickenRecipe = Recipe(
            label = "Roast Chicken Thighs",
            image = "",
            url = "",
            category = "Chicken",
            ingredientLines = listOf("Chicken Thigh", "Onion", "Garlic", "Rosemary")
        )

        val salmonRecipe = Recipe(
            label = "Baked Salmon",
            image = "",
            url = "",
            category = "Seafood",
            ingredientLines = listOf("Salmon Fillet", "Lemon", "Dill")
        )

        assertTrue(shouldKeepRecipeForProteins(chickenRecipe, userIngredients))
        assertTrue(shouldKeepRecipeForProteins(salmonRecipe, userIngredients))
    }

    @Test
    fun `countMissingIngredients ignores trivial garnishes like parsley and lemon wedges`() {
        val recipe = Recipe(
            label = "Pan Seared Salmon",
            image = "",
            url = "",
            category = "Seafood",
            ingredientLines = listOf(
                "2 Salmon Fillets",
                "1 tbsp Olive Oil",
                "1/2 tsp Salt",
                "Chopped fresh parsley for garnish",
                "Lemon wedges to serve"
            )
        )

        // User only owns salmon and the staples (oil and salt), NO parsley or lemon wedges
        val owned = setOf("Salmon", "Olive Oil", "Salt")
        val missingCount = countMissingIngredients(recipe, owned)

        // The parsley and lemon wedges should be recognized as trivial garnishes and ignored
        assertEquals(0, missingCount)
    }

    @Test
    fun `isTrivialOrGarnish correctly identifies garnishes and serving measures`() {
        assertTrue(isTrivialOrGarnish("Chopped fresh parsley for garnish"))
        assertTrue(isTrivialOrGarnish("Lemon wedges to serve"))
        assertTrue(isTrivialOrGarnish("Toasted sesame seeds"))
        assertTrue(isTrivialOrGarnish("1 pinch of black pepper"))
        assertTrue(isTrivialOrGarnish("Sprig of fresh mint"))
        assertFalse(isTrivialOrGarnish("500g Chicken Breast"))
        assertFalse(isTrivialOrGarnish("2 Salmon Fillets"))
        assertFalse(isTrivialOrGarnish("1 large Yellow Onion"))
    }
}
