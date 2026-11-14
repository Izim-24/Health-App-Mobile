package com.healthapp.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class ChatMessageDto(val role: String, val content: String)
data class ChatRequestDto(val messages: List<ChatMessageDto>)
data class ChatResponseDto(val answer: String)

data class FoodImageRequestDto(val image: String, val mimeType: String)

data class FoodItemDto(
    val name: String?,
    @SerializedName("estimated_grams") val estimatedGrams: Double?,
    val calories: Double?,
    @SerializedName("protein_g") val proteinG: Double?,
    @SerializedName("carbs_g") val carbsG: Double?,
    @SerializedName("fat_g") val fatG: Double?,
    val confidence: Double?
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
