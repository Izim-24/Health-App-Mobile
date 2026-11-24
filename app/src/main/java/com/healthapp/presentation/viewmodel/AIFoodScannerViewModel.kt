package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import com.healthapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.model.Food
import com.healthapp.domain.model.Meal
import com.healthapp.domain.model.MealItem
import com.healthapp.domain.model.MealType
import com.healthapp.domain.repository.AIRepository
import com.healthapp.domain.repository.NutritionRepository
import com.healthapp.domain.usecase.FoodScan
import com.healthapp.domain.usecase.ScanTotals
import com.healthapp.domain.usecase.ScannedFood
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class AIFoodScannerUiState(
    val imageUri: String? = null,
    val isAnalyzing: Boolean = false,
    val items: List<ScannedFood> = emptyList(),
    val notes: String? = null,
    val isMock: Boolean = false,
    val mealType: MealType = MealType.SNACK,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: UiText? = null
) {
    val totals: ScanTotals get() = FoodScan.totals(items)
    val hasResult: Boolean get() = items.isNotEmpty()
}

@HiltViewModel
class AIFoodScannerViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val nutritionRepository: NutritionRepository,
    private val accountManager: AccountSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIFoodScannerUiState(mealType = mealTypeFor(LocalTime.now())))
    val uiState: StateFlow<AIFoodScannerUiState> = _uiState.asStateFlow()

    fun analyzeImage(imageUri: String) {
        _uiState.update { it.copy(imageUri = imageUri, isAnalyzing = true, items = emptyList(), notes = null, isMock = false, error = null, saved = false) }
        viewModelScope.launch {
            aiRepository.analyzeFoodImage(imageUri)
                .onSuccess { analysis ->
                    val items = FoodScan.fromRecognized(analysis.recognizedFoods)
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false, items = items, notes = analysis.notes, isMock = analysis.isMock,
                            error = if (items.isEmpty()) UiText.Res(R.string.err_no_foods) else null
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isAnalyzing = false, error = e.message?.let(UiText::Raw) ?: UiText.Res(R.string.err_analyze)) } }
        }
    }

    fun setGrams(index: Int, grams: Int) = _uiState.update { s ->
        s.copy(items = s.items.mapIndexed { i, f -> if (i == index) f.withGrams(grams) else f })
    }

    /** Sets one item's portion to a multiple of the AI estimate. */
    fun setPortion(index: Int, factor: Double) = _uiState.update { s ->
        s.copy(items = s.items.mapIndexed { i, f -> if (i == index) f.scaled(factor) else f })
    }

    /** Scales every item relative to its AI estimate (e.g. "my plate was bigger than it looks"). */
    fun scaleAll(factor: Double) = _uiState.update { s -> s.copy(items = s.items.map { it.scaled(factor) }) }

    fun removeItem(index: Int) = _uiState.update { s -> s.copy(items = s.items.filterIndexed { i, _ -> i != index }) }

    fun setMealType(type: MealType) = _uiState.update { it.copy(mealType = type) }

    /** Saves the reviewed items as one meal. Each item becomes a food row tagged AI_SCANNED. */
    fun save() {
        val state = _uiState.value
        if (state.items.isEmpty() || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val accountId = accountManager.getCurrentAccountId()
                val mealItems = state.items.map { item ->
                    val food = Food(
                        id = UUID.randomUUID().toString(), name = item.name, calories = item.calories,
                        protein = item.protein, carbohydrates = item.carbs, fat = item.fat,
                        fiber = 0.0, sugar = 0.0, sodium = 0, servingSize = item.grams, source = "AI_SCANNED"
                    )
                    nutritionRepository.addCustomFood(food).getOrThrow()
                    MealItem(
                        id = UUID.randomUUID().toString(), food = food, quantity = 1.0, portionGrams = item.grams,
                        calories = item.calories, protein = item.protein, carbohydrates = item.carbs, fat = item.fat,
                        fiber = 0.0, sugar = 0.0, sodium = 0
                    )
                }
                val meal = Meal(
                    id = UUID.randomUUID().toString(), mealType = state.mealType, mealDate = LocalDate.now().toString(),
                    totalCalories = mealItems.sumOf { it.calories }, totalProtein = mealItems.sumOf { it.protein },
                    totalCarbs = mealItems.sumOf { it.carbohydrates }, totalFat = mealItems.sumOf { it.fat },
                    totalFiber = 0.0, totalSugar = 0.0, totalSodium = 0, items = mealItems,
                    notes = if (state.isMock) "Ước tính AI (GIẢ LẬP)" else "Ước tính AI từ ảnh", createdAt = LocalDateTime.now()
                )
                nutritionRepository.addMeal(accountId, meal).getOrThrow()
            }.onSuccess { _uiState.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message?.let(UiText::Raw) ?: UiText.Res(R.string.err_save_meal)) } }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun reset() { _uiState.value = AIFoodScannerUiState(mealType = mealTypeFor(LocalTime.now())) }

    companion object {
        fun mealTypeFor(time: LocalTime): MealType = when (time.hour) {
            in 5..9 -> MealType.BREAKFAST
            in 10..14 -> MealType.LUNCH
            in 17..21 -> MealType.DINNER
            else -> MealType.SNACK
        }
    }
}
