package com.healthapp.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface OpenFoodFactsApi {
    @Headers("User-Agent: HealthApp/1.0.0 (Android nutrition lookup)")
    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") query: String,
        @Query("search_simple") simple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("fields") fields: String = "code,product_name,brands,nutriments"
    ): OpenFoodFactsSearchResponse
}

data class OpenFoodFactsSearchResponse(
    @SerializedName("products") val products: List<OpenFoodFactsProduct> = emptyList()
)

data class OpenFoodFactsProduct(
    @SerializedName("code") val code: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("brands") val brands: String? = null,
    @SerializedName("nutriments") val nutrients: OpenFoodFactsNutrients? = null
)

data class OpenFoodFactsNutrients(
    @SerializedName("energy-kcal_100g") val caloriesPer100g: Double? = null,
    @SerializedName("proteins_100g") val proteinPer100g: Double? = null,
    @SerializedName("carbohydrates_100g") val carbsPer100g: Double? = null,
    @SerializedName("fat_100g") val fatPer100g: Double? = null,
    @SerializedName("fiber_100g") val fiberPer100g: Double? = null,
    @SerializedName("sugars_100g") val sugarPer100g: Double? = null,
    @SerializedName("sodium_100g") val sodiumGramsPer100g: Double? = null
)
