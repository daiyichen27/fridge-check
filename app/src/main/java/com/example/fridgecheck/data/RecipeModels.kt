package com.example.fridgecheck.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Response for single-ingredient filter: filter.php?i={ingredient}
data class MealDBFilterResponse(
    val meals: List<MealSummary>?
)

data class MealSummary(
    val idMeal: String,
    val strMeal: String,
    val strMealThumb: String?
)

// Response for full meal lookup: lookup.php?i={id}
data class MealDBLookupResponse(
    val meals: List<MealDetail>?
)

data class MealDetail(
    val idMeal: String,
    val strMeal: String,
    val strMealThumb: String? = null,
    val strInstructions: String? = null,
    val strSource: String? = null,
    val strYoutube: String? = null,
    val strIngredient1: String? = null,
    val strIngredient2: String? = null,
    val strIngredient3: String? = null,
    val strIngredient4: String? = null,
    val strIngredient5: String? = null,
    val strIngredient6: String? = null,
    val strIngredient7: String? = null,
    val strIngredient8: String? = null,
    val strIngredient9: String? = null,
    val strIngredient10: String? = null,
    val strIngredient11: String? = null,
    val strIngredient12: String? = null,
    val strIngredient13: String? = null,
    val strIngredient14: String? = null,
    val strIngredient15: String? = null,
    val strIngredient16: String? = null,
    val strIngredient17: String? = null,
    val strIngredient18: String? = null,
    val strIngredient19: String? = null,
    val strIngredient20: String? = null,
    val strMeasure1: String? = null,
    val strMeasure2: String? = null,
    val strMeasure3: String? = null,
    val strMeasure4: String? = null,
    val strMeasure5: String? = null,
    val strMeasure6: String? = null,
    val strMeasure7: String? = null,
    val strMeasure8: String? = null,
    val strMeasure9: String? = null,
    val strMeasure10: String? = null,
    val strMeasure11: String? = null,
    val strMeasure12: String? = null,
    val strMeasure13: String? = null,
    val strMeasure14: String? = null,
    val strMeasure15: String? = null,
    val strMeasure16: String? = null,
    val strMeasure17: String? = null,
    val strMeasure18: String? = null,
    val strMeasure19: String? = null,
    val strMeasure20: String? = null
) {
    fun toIngredientLines(): List<String> {
        val pairs = listOf(
            strIngredient1 to strMeasure1,
            strIngredient2 to strMeasure2,
            strIngredient3 to strMeasure3,
            strIngredient4 to strMeasure4,
            strIngredient5 to strMeasure5,
            strIngredient6 to strMeasure6,
            strIngredient7 to strMeasure7,
            strIngredient8 to strMeasure8,
            strIngredient9 to strMeasure9,
            strIngredient10 to strMeasure10,
            strIngredient11 to strMeasure11,
            strIngredient12 to strMeasure12,
            strIngredient13 to strMeasure13,
            strIngredient14 to strMeasure14,
            strIngredient15 to strMeasure15,
            strIngredient16 to strMeasure16,
            strIngredient17 to strMeasure17,
            strIngredient18 to strMeasure18,
            strIngredient19 to strMeasure19,
            strIngredient20 to strMeasure20
        )
        return pairs.mapNotNull { (ingredient, measure) ->
            val cleanIng = ingredient?.trim()
            if (cleanIng.isNullOrEmpty()) null
            else {
                val cleanMeasure = measure?.trim().orEmpty()
                if (cleanMeasure.isNotEmpty()) "$cleanMeasure $cleanIng" else cleanIng
            }
        }
    }

    fun toRecipe(): Recipe {
        val recipeUrl = if (!strSource.isNullOrBlank() && strSource.startsWith("http")) {
            strSource
        } else {
            "https://www.themealdb.com/meal/$idMeal"
        }
        return Recipe(
            id = idMeal,
            label = strMeal,
            image = strMealThumb.orEmpty(),
            url = recipeUrl,
            source = "TheMealDB",
            ingredientLines = toIngredientLines()
        )
    }
}

fun MealSummary.toFallbackRecipe(): Recipe {
    return Recipe(
        id = idMeal,
        label = strMeal,
        image = strMealThumb.orEmpty(),
        url = "https://www.themealdb.com/meal/$idMeal",
        source = "TheMealDB",
        ingredientLines = emptyList()
    )
}

data class Recipe(
    val id: String = "",
    val label: String,
    val image: String,
    val url: String,
    val source: String = "TheMealDB",
    val ingredientLines: List<String> = emptyList()
)

interface RecipeService {
    @GET("api/json/v1/{apiKey}/filter.php")
    suspend fun filterByIngredient(
        @Path("apiKey") apiKey: String,
        @Query("i") ingredient: String
    ): MealDBFilterResponse

    @GET("api/json/v1/{apiKey}/lookup.php")
    suspend fun lookupMealById(
        @Path("apiKey") apiKey: String,
        @Query("i") id: String
    ): MealDBLookupResponse
}
