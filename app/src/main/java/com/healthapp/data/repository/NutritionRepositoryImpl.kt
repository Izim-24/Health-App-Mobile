package com.healthapp.data.repository

import com.healthapp.data.local.dao.*
import com.healthapp.data.local.entity.*
import com.healthapp.data.remote.OpenFoodFactsApi
import com.healthapp.domain.model.*
import com.healthapp.domain.repository.NutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

class NutritionRepositoryImpl @Inject constructor(
    private val foodDao: FoodDao,
    private val mealDao: MealDao,
    private val mealItemDao: MealItemDao,
    private val profileDao: UserProfileDao,
    private val openFoodFactsApi: OpenFoodFactsApi
) : NutritionRepository {

    override suspend fun addMeal(userId: String, meal: Meal): Result<Meal> = try {
        // Insert items BEFORE the meal: the meal list observes only the meals table, so the
        // meal insert must be the last write or the UI reads the meal with no items yet.
        meal.items.forEach { item ->
            mealItemDao.insertMealItem(item.toEntity(meal.id))
        }
        mealDao.insertMeal(meal.toEntity(userId))

        Result.success(meal)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateMeal(userId: String, meal: Meal): Result<Unit> = try {
        val mealEntity = meal.toEntity(userId)
        mealDao.updateMeal(mealEntity)

        mealItemDao.deleteMealItemsByMealId(meal.id)
        meal.items.forEach { item ->
            mealItemDao.insertMealItem(item.toEntity(meal.id))
        }

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteMeal(mealId: String): Result<Unit> = try {
        mealItemDao.deleteMealItemsByMealId(mealId)
        mealDao.deleteMeal(mealId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getMeal(mealId: String): Result<Meal> = try {
        val mealEntity = mealDao.getMealById(mealId)
        if (mealEntity != null) {
            val items = mealItemDao.getMealItemsByMealId(mealId)
            Result.success(mealEntity.toDomain(items))
        } else {
            Result.failure(Exception("Meal not found"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun observeMealsByDate(userId: String, date: String): Flow<List<Meal>> =
        mealDao.observeMealsByDate(userId, date).map { meals ->
            meals.map { mealEntity ->
                val items = mealItemDao.getMealItemsByMealId(mealEntity.id)
                mealEntity.toDomain(items)
            }
        }

    override suspend fun getNutritionSummary(userId: String, date: String): Result<NutritionSummary> = try {
        val totalCalories = mealDao.getTotalCaloriesForDate(userId, date)
        val totalProtein = mealDao.getTotalProteinForDate(userId, date)
        val totalCarbs = mealDao.getTotalCarbsForDate(userId, date)
        val totalFat = mealDao.getTotalFatForDate(userId, date)
        val profile = profileDao.getProfileByAccountId(userId)

        Result.success(
            NutritionSummary(
                date = date,
                totalCalories = totalCalories,
                totalProtein = totalProtein,
                totalCarbs = totalCarbs,
                totalFat = totalFat,
                targetCalories = profile?.targetCalories ?: 0,
                targetProtein = profile?.targetProtein ?: 0.0,
                targetCarbs = profile?.targetCarbs ?: 0.0,
                targetFat = profile?.targetFat ?: 0.0
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun searchFoods(query: String): Result<List<Food>> = try {
        val localFoods = foodDao.searchFoods(query).filter { it.source != "INTEGRATED_LIBRARY" && it.source != "AI_SCANNED" }.map { it.toDomain() }
        val onlineFoods = if (query.trim().length >= 2) {
            runCatching {
                openFoodFactsApi.searchProducts(query.trim()).products.mapNotNull { product ->
                    val name = product.productName?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val nutrients = product.nutrients ?: return@mapNotNull null
                    val calories = nutrients.caloriesPer100g?.takeIf { it >= 0 } ?: return@mapNotNull null
                    val protein = nutrients.proteinPer100g?.takeIf { it >= 0 } ?: return@mapNotNull null
                    val carbs = nutrients.carbsPer100g?.takeIf { it >= 0 } ?: return@mapNotNull null
                    val fat = nutrients.fatPer100g?.takeIf { it >= 0 } ?: return@mapNotNull null
                    Food(
                        id = "off-${product.code ?: name.hashCode()}",
                        name = name,
                        calories = calories.toInt(),
                        protein = protein,
                        carbohydrates = carbs,
                        fat = fat,
                        fiber = nutrients.fiberPer100g ?: 0.0,
                        sugar = nutrients.sugarPer100g ?: 0.0,
                        sodium = ((nutrients.sodiumGramsPer100g ?: 0.0) * 1000).toInt(),
                        servingSize = 100,
                        source = "OPEN_FOOD_FACTS"
                    )
                }.also { foods -> foodDao.insertFoods(foods.map { it.toEntity() }) }
            }.getOrDefault(emptyList())
        } else emptyList()
        Result.success((onlineFoods + localFoods).distinctBy { it.name.trim().lowercase() })
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun addCustomFood(food: Food): Result<Food> = try {
        val entity = food.toEntity()
        foodDao.insertFood(entity)
        Result.success(food)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun seedDefaultFoods(): Result<Unit> = Result.success(Unit)

    private fun Meal.toEntity(userId: String) = MealEntity(
        id = this.id,
        accountId = userId,
        mealType = this.mealType.name,
        mealDate = this.mealDate,
        totalCalories = this.totalCalories,
        totalProtein = this.totalProtein,
        totalCarbs = this.totalCarbs,
        totalFat = this.totalFat,
        totalFiber = this.totalFiber,
        totalSugar = this.totalSugar,
        totalSodium = this.totalSodium,
        notes = this.notes,
        imageUri = this.imageUri,
        createdAt = this.createdAt,
        updatedAt = LocalDateTime.now()
    )

    private suspend fun MealEntity.toDomain(items: List<MealItemEntity>) = Meal(
        id = this.id,
        mealType = MealType.valueOf(this.mealType),
        mealDate = this.mealDate,
        totalCalories = this.totalCalories,
        totalProtein = this.totalProtein,
        totalCarbs = this.totalCarbs,
        totalFat = this.totalFat,
        totalFiber = this.totalFiber,
        totalSugar = this.totalSugar,
        totalSodium = this.totalSodium,
        items = items.map { item -> item.toDomain(foodDao.getFoodById(item.foodId)?.toDomain()) },
        notes = this.notes,
        imageUri = this.imageUri,
        createdAt = this.createdAt
    )

    private fun MealItem.toEntity(mealId: String) = MealItemEntity(
        id = this.id,
        mealId = mealId,
        foodId = this.food.id,
        quantity = this.quantity,
        portionGrams = this.portionGrams,
        calories = this.calories,
        protein = this.protein,
        carbohydrates = this.carbohydrates,
        fat = this.fat,
        fiber = this.fiber,
        sugar = this.sugar,
        sodium = this.sodium
    )

    private fun MealItemEntity.toDomain(food: Food?) = MealItem(
        id = this.id,
        food = food ?: Food(this.foodId, "Thực phẩm", this.calories, this.protein, this.carbohydrates, this.fat, this.fiber, this.sugar, this.sodium, this.portionGrams),
        quantity = this.quantity,
        portionGrams = this.portionGrams,
        calories = this.calories,
        protein = this.protein,
        carbohydrates = this.carbohydrates,
        fat = this.fat,
        fiber = this.fiber,
        sugar = this.sugar,
        sodium = this.sodium
    )

    private fun Food.toEntity() = FoodEntity(
        id = this.id,
        name = this.name,
        calories = this.calories,
        protein = this.protein,
        carbohydrates = this.carbohydrates,
        fat = this.fat,
        fiber = this.fiber,
        sugar = this.sugar,
        sodium = this.sodium,
        servingSize = this.servingSize,
        source = this.source
    )

    private fun FoodEntity.toDomain() = Food(
        id = this.id,
        name = this.name,
        calories = this.calories,
        protein = this.protein,
        carbohydrates = this.carbohydrates,
        fat = this.fat,
        fiber = this.fiber,
        sugar = this.sugar,
        sodium = this.sodium,
        servingSize = this.servingSize,
        source = this.source
    )
}
