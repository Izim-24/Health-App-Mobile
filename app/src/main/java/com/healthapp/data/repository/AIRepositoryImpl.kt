package com.healthapp.data.repository

import android.content.Context
import com.healthapp.data.remote.AiBackendApi
import com.healthapp.data.remote.ChatMessageDto
import com.healthapp.data.remote.ChatRequestDto
import com.healthapp.domain.model.AIFoodAnalysis
import com.healthapp.domain.repository.AIRepository
import javax.inject.Inject

/** AI requests must go through a server that owns the provider key. */
class AIRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val aiBackendApi: AiBackendApi
) : AIRepository {
    private val conversations = mutableMapOf<String, List<ChatMessageDto>>()

    /**
     * Compresses the photo, sends it to the backend (which owns the API key) and maps the
     * structured estimate. The photo is sent off-device; the UI tells the user before they pick one.
     */
    override suspend fun analyzeFoodImage(imageUri: String): Result<AIFoodAnalysis> = runCatching {
        val encoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.healthapp.data.remote.ImageEncoder.encode(context, android.net.Uri.parse(imageUri))
        }
        val response = try {
            aiBackendApi.analyzeFood(com.healthapp.data.remote.FoodImageRequestDto(encoded.base64, encoded.mimeType))
        } catch (e: retrofit2.HttpException) {
            throw IllegalStateException(backendError(e))
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Không kết nối được máy chủ AI. Kiểm tra backend trong thư mục server đã chạy chưa.", e)
        }
        val foods = response.foods.orEmpty().mapNotNull { dto ->
            val name = dto.name?.trim().orEmpty()
            val grams = dto.estimatedGrams ?: return@mapNotNull null
            val kcal = dto.calories ?: return@mapNotNull null
            if (name.isEmpty()) return@mapNotNull null
            com.healthapp.domain.model.RecognizedFood(
                name = name,
                calories = kcal.toInt(),
                protein = dto.proteinG ?: 0.0,
                carbohydrates = dto.carbsG ?: 0.0,
                fat = dto.fatG ?: 0.0,
                estimatedWeightGrams = grams.toInt(),
                confidence = (dto.confidence ?: 0.0).toFloat()
            )
        }
        if (response.isFood == false || foods.isEmpty()) {
            throw IllegalStateException("Không nhận ra món ăn trong ảnh. Thử chụp rõ hơn, từ trên xuống, đủ sáng.")
        }
        AIFoodAnalysis(
            recognizedFoods = foods,
            totalCalories = foods.sumOf { it.calories },
            totalProtein = foods.sumOf { it.protein },
            totalCarbs = foods.sumOf { it.carbohydrates },
            totalFat = foods.sumOf { it.fat },
            confidenceLevel = foods.map { it.confidence }.average().toFloat(),
            notes = response.notes?.takeIf { it.isNotBlank() },
            isMock = response.mock == true
        )
    }

    private fun backendError(e: retrofit2.HttpException): String {
        val serverMessage = runCatching {
            com.google.gson.JsonParser().parse(e.response()?.errorBody()?.string()).asJsonObject.get("error").asString
        }.getOrNull()
        return serverMessage ?: when (e.code()) {
            413 -> "Ảnh quá lớn."
            429 -> "Bạn gửi quá nhiều ảnh, thử lại sau một phút."
            503 -> "Máy chủ AI chưa được cấu hình khóa API."
            else -> "Máy chủ AI lỗi (${e.code()})."
        }
    }


    override suspend fun chatWithAssistant(userId: String, query: String): Result<String> = runCatching {
        require(query.isNotBlank() && query.length <= 2000) { "Câu hỏi cần từ 1 đến 2000 ký tự." }
        val history = conversations[userId].orEmpty().takeLast(10)
        val next = history + ChatMessageDto("user", query.trim())
        val response = aiBackendApi.chat(ChatRequestDto(next))
        require(response.answer.isNotBlank()) { "Trợ lý chưa trả về câu trả lời." }
        conversations[userId] = (next + ChatMessageDto("assistant", response.answer)).takeLast(10)
        response.answer
    }

}
