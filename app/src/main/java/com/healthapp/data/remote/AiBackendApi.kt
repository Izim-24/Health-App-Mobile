package com.healthapp.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class ChatMessageDto(val role: String, val content: String)
/** [lang] ("vi"/"en") is the app language; the backend answers in it. */
data class ChatRequestDto(val messages: List<ChatMessageDto>, val lang: String = "vi")
data class ChatResponseDto(val answer: String)

data class FoodImageRequestDto(val image: String, val mimeType: String, val lang: String = "vi")

data class FoodItemDto(
    val name: String?,
    @SerializedName("estimated_grams") val estimatedGrams: Double?,
    val calories: Double?,
    @SerializedName("protein_g") val proteinG: Double?,
    @SerializedName("carbs_g") val carbsG: Double?,
    @SerializedName("fat_g") val fatG: Double?,
    val confidence: Double?,
    /** "table" = calories from the food-composition table × grams; "ai" = the model's own estimate. */
    val source: String? = null
)

data class FoodImageResponseDto(
    @SerializedName("is_food") val isFood: Boolean?,
    val foods: List<FoodItemDto>?,
    val notes: String?,
    val mock: Boolean?
)

interface AiBackendApi {
    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequestDto): ChatResponseDto

    @POST("api/food-image")
    suspend fun analyzeFood(@Body request: FoodImageRequestDto): FoodImageResponseDto
}
