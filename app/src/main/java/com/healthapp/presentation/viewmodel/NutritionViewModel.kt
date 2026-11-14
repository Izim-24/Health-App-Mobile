package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.data.repository.AccountManager
import com.healthapp.domain.model.Food
import com.healthapp.domain.model.Meal
import com.healthapp.domain.model.MealItem
import com.healthapp.domain.model.MealType
import com.healthapp.domain.model.NutritionSummary
import com.healthapp.domain.repository.NutritionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class NutritionUiState(
    val isLoading: Boolean = true,
    val isSearchingFoods: Boolean = false,
    val error: String? = null,
    val meals: List<Meal> = emptyList(),
    val summary: NutritionSummary? = null,
    val foods: List<Food> = emptyList()
)

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val accountManager: AccountManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var mealJob: Job? = null

    init {
        viewModelScope.launch {
            searchFoods("")
            observeToday()
            while (true) {
                val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                delay((nextMidnight - System.currentTimeMillis()).coerceAtLeast(1000L))
                observeToday()
            }
        }
    }

    private suspend fun observeToday() {
        mealJob?.cancel()
        val accountId = accountManager.getCurrentAccountId()
        val date = LocalDate.now().toString()
        _uiState.update { it.copy(isLoading = true, meals = emptyList(), summary = null) }
        mealJob = viewModelScope.launch {
            repository.observeMealsByDate(accountId, date).collect { meals ->
                _uiState.update { it.copy(meals = meals, isLoading = false) }
                refreshSummary(accountId, date)
            }
        }
    }

    fun searchFoods(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingFoods = true, error = null) }
            repository.searchFoods(query).onSuccess { foods -> _uiState.update { it.copy(foods = foods, isSearchingFoods = false) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message, isSearchingFoods = false) } }
        }
    }

    fun addFood(food: Food, portionGrams: Int, mealType: MealType) {
        if (portionGrams <= 0 || food.servingSize <= 0) return
        viewModelScope.launch {
            try {
                val accountId = accountManager.getCurrentAccountId()
                val now = LocalDateTime.now()
                val multiplier = portionGrams.toDouble() / food.servingSize
                val item = MealItem(
                    id = UUID.randomUUID().toString(), food = food, quantity = multiplier, portionGrams = portionGrams,
                    calories = (food.calories * multiplier).toInt(), protein = food.protein * multiplier,
                    carbohydrates = food.carbohydrates * multiplier, fat = food.fat * multiplier,
                    fiber = food.fiber * multiplier, sugar = food.sugar * multiplier, sodium = (food.sodium * multiplier).toInt()
                )
                val meal = Meal(
                    id = UUID.randomUUID().toString(), mealType = mealType, mealDate = LocalDate.now().toString(),
                    totalCalories = item.calories, totalProtein = item.protein, totalCarbs = item.carbohydrates,
                    totalFat = item.fat, totalFiber = item.fiber, totalSugar = item.sugar, totalSodium = item.sodium,
                    items = listOf(item), createdAt = now
                )
                repository.addMeal(accountId, meal).getOrThrow()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun addCustomFood(name: String, calories: Int, protein: Double, carbs: Double, fat: Double) {
        if (name.isBlank() || calories !in 0..2000 || listOf(protein, carbs, fat).any { !it.isFinite() || it < 0 || it > 100 }) {
            _uiState.update { it.copy(error = "Kiểm tra tên và thành phần cho 100 g thực phẩm.") }
            return
        }
        viewModelScope.launch {
            val food = Food(UUID.randomUUID().toString(), name.trim(), calories, protein, carbs, fat, 0.0, 0.0, 0, 100, "USER_CREATED")
            repository.addCustomFood(food).onSuccess { searchFoods("") }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch {
            repository.deleteMeal(mealId).onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    private suspend fun refreshSummary(accountId: String, date: String) {
        val summary = repository.getNutritionSummary(accountId, date).getOrNull()
        _uiState.update { it.copy(summary = summary) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
