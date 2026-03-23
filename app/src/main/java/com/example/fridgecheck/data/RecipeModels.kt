package com.example.fridgecheck.data

import retrofit2.http.GET
import retrofit2.http.Query

// The "Envelope" Edamam sends back
data class EdamamResponse(
    val hits: List<Hit>
)

data class Hit(
    val recipe: Recipe
)

data class Recipe(
    val label: String,
    val image: String,
    val url: String,
    val source: String,
    val ingredientLines: List<String> // The full list from Edamam
)

interface RecipeService {
    @GET("api/recipes/v2")
    suspend fun searchRecipes(
        @Query("type") type: String = "public",
        @Query("q") query: String, // Our comma-separated ingredients
        @Query("app_id") appId: String,
        @Query("app_key") appKey: String
    ): EdamamResponse
}