package com.healthapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.time.LocalDateTime

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val email: String,
    val passwordHash: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val fullName: String,
    val dateOfBirth: String,
    val gender: String, // "MALE", "FEMALE", "OTHER"
    val height: Int, // cm
    val weight: Double, // kg
    val activityLevel: String, // "SEDENTARY", "LIGHTLY_ACTIVE", "MODERATELY_ACTIVE", "VERY_ACTIVE", "EXTRA_ACTIVE"
    val targetCalories: Int,
    val targetProtein: Double,
    val targetCarbs: Double,
    val targetFat: Double,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val name: String,
    val description: String?,
    val frequency: String, // "DAILY", "WEEKLY"
    val daysOfWeek: String?, // JSON array for weekly habits
    val reminderTime: String?,
    val goalValue: Double? = null,
    val goalUnit: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

@Entity(tableName = "habit_logs")
data class HabitLogEntity(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val completionDate: String, // YYYY-MM-DD
    val status: Boolean, // completed or not
    val recordedAt: LocalDateTime
)

@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val calories: Int,
    val protein: Double, // grams
    val carbohydrates: Double, // grams
    val fat: Double, // grams
    val fiber: Double, // grams
    val sugar: Double, // grams
    val sodium: Int, // mg
    val servingSize: Int, // grams
    val source: String // "DATABASE", "USER_CREATED", "AI_SCANNED"
)

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val mealType: String, // "BREAKFAST", "LUNCH", "DINNER", "SNACK"
    val mealDate: String, // YYYY-MM-DD
    val totalCalories: Int,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val totalFiber: Double,
    val totalSugar: Double,
    val totalSodium: Int,
    val notes: String?,
    val imageUri: String?, // URI of the meal photo
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

@Entity(tableName = "meal_items")
data class MealItemEntity(
    @PrimaryKey
    val id: String,
    val mealId: String,
    val foodId: String,
    val quantity: Double, // portion quantity
    val portionGrams: Int,
    val calories: Int,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodium: Int
)

@Entity(tableName = "water_logs")
data class WaterLogEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val amountMl: Int,
    val recordedAt: LocalDateTime,
    val date: String // YYYY-MM-DD
)

@Entity(tableName = "sleep_logs")
data class SleepLogEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val bedtime: LocalDateTime,
    val wakeupTime: LocalDateTime,
    val durationMinutes: Int,
    val sleepQuality: Int?, // 1-10 scale
    val notes: String?,
    val date: String // YYYY-MM-DD
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val activityType: String, // "WALKING", "RUNNING", "CYCLING", "YOGA", "GYM", etc.
    val durationMinutes: Int,
    val steps: Int,
    val distance: Double, // kilometers
    val caloriesBurned: Int,
    val intensity: String, // "LOW", "MODERATE", "HIGH"
    val recordedAt: LocalDateTime,
    val date: String // YYYY-MM-DD
)

@Entity(tableName = "weight_logs")
data class WeightLogEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val weight: Double, // kg
    val recordedAt: LocalDateTime,
    val date: String // YYYY-MM-DD
)

@Entity(tableName = "vital_logs", indices = [Index(value = ["accountId", "type", "recordedAt"])])
data class VitalLogEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: String,
    val value: Double,
    val secondaryValue: Double?,
    val unit: String,
    val source: String,
    val recordedAt: LocalDateTime
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val title: String,
    val reminderType: String, // "WATER", "EXERCISE", "SLEEP", "HABIT", "MEAL"
    val reminderTime: String, // HH:mm format
    val isEnabled: Boolean = true,
    val daysOfWeek: String?, // JSON array
    val createdAt: LocalDateTime
)

@Entity(tableName = "ai_analyses")
data class AIAnalysisEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val analysisType: String, // "NUTRITION", "HABIT", "SLEEP", "ACTIVITY", "WELLNESS"
    val result: String, // JSON string with analysis results
    val createdAt: LocalDateTime
)

@Entity(tableName = "medications", indices = [Index(value = ["accountId"])])
data class MedicationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val dosage: String,
    val instructions: String?,
    val times: String, // "08:00,20:00"
    val daysOfWeek: String?, // "MONDAY,FRIDAY"; null = every day
    val startDate: String, // YYYY-MM-DD
    val endDate: String?, // YYYY-MM-DD inclusive
    val isActive: Boolean = true,
    val createdAt: LocalDateTime
)

@Entity(
    tableName = "medication_logs",
    indices = [Index(value = ["medicationId", "date", "time"], unique = true), Index(value = ["date"])]
)
data class MedicationLogEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:mm dose slot
    val status: String, // "TAKEN", "SKIPPED"
    val recordedAt: LocalDateTime
)
