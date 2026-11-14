package com.healthapp.domain.model

import java.time.LocalDateTime

data class User(
    val id: String,
    val email: String,
    val fullName: String,
    val dateOfBirth: String,
    val gender: Gender,
    val height: Int, // cm
    val weight: Double, // kg
    val activityLevel: ActivityLevel,
    val targetCalories: Int,
    val targetProtein: Double,
    val targetCarbs: Double,
    val targetFat: Double
) {
    fun calculateBMI(): Double {
        val heightM = height / 100.0
        return weight / (heightM * heightM)
    }

    fun getBMIStatus(): BMIStatus {
        val bmi = calculateBMI()
        return when {
            // Asia-Pacific cut-offs, shared with the rest of the app.
            else -> when (com.healthapp.domain.usecase.HealthCalculations.bmiCategory(bmi)) {
                com.healthapp.domain.usecase.BmiCategory.SEVERELY_UNDERWEIGHT, com.healthapp.domain.usecase.BmiCategory.UNDERWEIGHT -> BMIStatus.UNDERWEIGHT
                com.healthapp.domain.usecase.BmiCategory.NORMAL -> BMIStatus.NORMAL
                com.healthapp.domain.usecase.BmiCategory.OVERWEIGHT -> BMIStatus.OVERWEIGHT
                com.healthapp.domain.usecase.BmiCategory.OBESE_1, com.healthapp.domain.usecase.BmiCategory.OBESE_2 -> BMIStatus.OBESE
            }
        }
    }
}

enum class Gender {
    MALE, FEMALE, OTHER
}

enum class ActivityLevel {
    SEDENTARY, // Little or no exercise
    LIGHTLY_ACTIVE, // Light exercise 1-3 days/week
    MODERATELY_ACTIVE, // Moderate exercise 3-5 days/week
    VERY_ACTIVE, // Hard exercise 6-7 days/week
    EXTRA_ACTIVE // Very hard exercise & physical job
}

enum class BMIStatus {
    UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE
}

data class Habit(
    val id: String,
    val accountId: String,
    val name: String,
    val description: String? = null,
    val frequency: HabitFrequency,
    val daysOfWeek: List<DayOfWeek>? = null,
    val reminderTime: String? = null,
    val isEnabled: Boolean = true,
    val streak: Int = 0,
    val completionRate: Double = 0.0,
    val createdAt: LocalDateTime,
    val goalValue: Double? = null,
    val goalUnit: String? = null,
    val isCompletedToday: Boolean = false
)

enum class HabitFrequency {
    DAILY, WEEKLY
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

data class Food(
    val id: String,
    val name: String,
    val calories: Int,
    val protein: Double, // grams
    val carbohydrates: Double, // grams
    val fat: Double, // grams
    val fiber: Double, // grams
    val sugar: Double, // grams
    val sodium: Int, // mg
    val servingSize: Int = 100, // grams
    val source: String = "INTEGRATED_LIBRARY"
)

data class Meal(
    val id: String,
    val mealType: MealType,
    val mealDate: String, // YYYY-MM-DD
    val totalCalories: Int,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val totalFiber: Double,
    val totalSugar: Double,
    val totalSodium: Int,
    val items: List<MealItem> = emptyList(),
    val notes: String? = null,
    val imageUri: String? = null,
    val createdAt: LocalDateTime
)

data class MealItem(
    val id: String,
    val food: Food,
    val quantity: Double, // portion quantity (e.g., 1.5 servings)
    val portionGrams: Int,
    val calories: Int,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodium: Int
)

enum class MealType {
    BREAKFAST, LUNCH, DINNER, SNACK
}

data class WaterIntake(
    val id: String,
    val amountMl: Int,
    val recordedAt: LocalDateTime,
    val date: String // YYYY-MM-DD
)

data class SleepRecord(
    val id: String,
    val bedtime: LocalDateTime,
    val wakeupTime: LocalDateTime,
    val durationMinutes: Int,
    val sleepQuality: Int?, // 1-10 scale
    val notes: String? = null,
    val date: String // YYYY-MM-DD
)

data class ActivityRecord(
    val id: String,
    val activityType: ActivityType,
    val durationMinutes: Int,
    val steps: Int,
    val distance: Double, // kilometers
    val caloriesBurned: Int,
    val intensity: ActivityIntensity,
    val recordedAt: LocalDateTime,
    val date: String // YYYY-MM-DD
)

enum class ActivityType {
    WALKING, RUNNING, CYCLING, SWIMMING, YOGA, GYM_WORKOUT, DANCING, HIIT, SPORTS, OTHER
}

enum class ActivityIntensity {
    LOW, MODERATE, HIGH
}

data class WeightRecord(
    val id: String,
    val weight: Double, // kg
    val recordedAt: LocalDateTime,
    val date: String // YYYY-MM-DD
)

data class VitalMeasurement(
    val id: String,
    val accountId: String,
    val type: VitalType,
    val value: Double,
    val secondaryValue: Double? = null,
    val unit: String,
    val source: String = "MANUAL",
    val recordedAt: LocalDateTime
)

enum class VitalType { HEART_RATE, BLOOD_PRESSURE, GLUCOSE }

data class Reminder(
    val id: String,
    val title: String,
    val reminderType: ReminderType,
    val reminderTime: String, // HH:mm format
    val isEnabled: Boolean = true,
    val daysOfWeek: List<DayOfWeek>? = null,
    val createdAt: LocalDateTime
)

enum class ReminderType {
    WATER, EXERCISE, SLEEP, HABIT, MEAL
}

data class NutritionSummary(
    val date: String,
    val totalCalories: Int = 0,
    val totalProtein: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalFiber: Double = 0.0,
    val totalSugar: Double = 0.0,
    val totalSodium: Int = 0,
    val waterIntakeMl: Int = 0,
    val targetCalories: Int = 0,
    val targetProtein: Double = 0.0,
    val targetCarbs: Double = 0.0,
    val targetFat: Double = 0.0
) {
    val remainingCalories get() = (targetCalories - totalCalories).coerceAtLeast(0)
    val remainingProtein get() = (targetProtein - totalProtein).coerceAtLeast(0.0)
    val remainingCarbs get() = (targetCarbs - totalCarbs).coerceAtLeast(0.0)
    val remainingFat get() = (targetFat - totalFat).coerceAtLeast(0.0)

    val calorieProgress get() = if (targetCalories > 0) totalCalories.toFloat() / targetCalories else 0f
    val proteinProgress get() = if (targetProtein > 0) totalProtein.toFloat() / targetProtein.toFloat() else 0f
    val carbsProgress get() = if (targetCarbs > 0) totalCarbs.toFloat() / targetCarbs.toFloat() else 0f
    val fatProgress get() = if (targetFat > 0) totalFat.toFloat() / targetFat.toFloat() else 0f
}

data class DailySummary(
    val date: String,
    val nutrition: NutritionSummary,
    val habitsCompleted: Int = 0,
    val totalHabits: Int = 0,
    val waterPercentage: Float = 0f,
    val sleepDurationHours: Float = 0f,
    val steps: Int = 0,
    val activityMinutes: Int = 0,
    val weight: Double? = null
) {
    val habitCompletionRate get() = if (totalHabits > 0) habitsCompleted.toFloat() / totalHabits.toFloat() else 0f
}

data class AIFoodAnalysis(
    val recognizedFoods: List<RecognizedFood>,
    val totalCalories: Int,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val confidenceLevel: Float, // 0.0 to 1.0
    val estimatedPortionSize: String? = null,
    val notes: String? = null,
    /** True when the backend ran in development mock mode: data is sample, not from the photo. */
    val isMock: Boolean = false
)

data class RecognizedFood(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Int = 0,
    val estimatedWeightGrams: Int? = null,
    val confidence: Float,
    val recognizedImageRegion: String? = null
)

data class AIHealthAnalysis(
    val analysisType: AIAnalysisType,
    val summary: String,
    val insights: List<String>,
    val suggestions: List<String>,
    val generatedAt: LocalDateTime
)

enum class AIAnalysisType {
    NUTRITION, HABIT, SLEEP, ACTIVITY, WELLNESS, CUSTOM
}
