package com.healthapp.data.repository

import com.healthapp.R
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
            aiBackendApi.analyzeFood(com.healthapp.data.remote.FoodImageRequestDto(encoded.base64, encoded.mimeType, lang()))
        } catch (e: retrofit2.HttpException) {
            throw IllegalStateException(backendError(e))
        } catch (e: java.io.IOException) {
            throw IllegalStateException(str(R.string.err_ai_unreachable), e)
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
                confidence = (dto.confidence ?: 0.0).toFloat(),
                caloriesFromTable = dto.source == "table"
            )
        }
        if (response.isFood == false || foods.isEmpty()) {
            throw IllegalStateException(str(R.string.err_ai_no_food))
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

    private fun str(id: Int, vararg args: Any) = com.healthapp.core.AppLocale.localized(context).getString(id, *args)

    /** "vi" or "en": the language the app is showing, so the AI answers in it. */
    private fun lang() = str(R.string.lang_code)

    /**
     * The backend sends a stable `code` with each error; map it to text in the app's language.
     * Unknown codes fall back to the server's own message.
     */
    private fun backendError(e: retrofit2.HttpException): String {
        val body = runCatching { @Suppress("DEPRECATION") com.google.gson.JsonParser().parse(e.response()?.errorBody()?.string()).asJsonObject }.getOrNull()
        val code = runCatching { body?.get("code")?.asString }.getOrNull()
        val res = when (code) {
            "rate_limited" -> R.string.err_ai_rate_limited
            "not_configured" -> R.string.err_ai_not_configured
            "upstream_auth" -> R.string.err_ai_auth
            "upstream_quota" -> R.string.err_ai_quota
            "upstream_error", "unreachable" -> R.string.err_ai_upstream
            "bad_output" -> R.string.err_ai_bad_output
            "bad_image" -> R.string.err_ai_bad_image
            "too_large" -> R.string.err_ai_too_large
            else -> when (e.code()) {
                413 -> R.string.err_ai_too_large
                429 -> R.string.err_ai_rate_limited
                503 -> R.string.err_ai_not_configured
                else -> null
            }
        }
        return res?.let { str(it) }
            ?: runCatching { body?.get("error")?.asString }.getOrNull()
            ?: str(R.string.err_ai_http, e.code())
    }


    override suspend fun chatWithAssistant(userId: String, query: String): Result<String> = runCatching {
        require(query.isNotBlank() && query.length <= 2000) { str(R.string.err_question_length) }
        val history = conversations[userId].orEmpty().takeLast(10)
        val next = history + ChatMessageDto("user", query.trim())
        val response = try {
            aiBackendApi.chat(ChatRequestDto(next, lang()))
        } catch (e: retrofit2.HttpException) {
            throw IllegalStateException(backendError(e))
        }
        require(response.answer.isNotBlank()) { str(R.string.err_ai_empty_answer) }
        conversations[userId] = (next + ChatMessageDto("assistant", response.answer)).takeLast(10)
        response.answer
    }

}
