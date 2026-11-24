package com.healthapp.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Personal data the user enters on the Profile screen. Zero/null means "not entered". */
data class HealthProfile(
    val fullName: String = "",
    val dateOfBirth: String = "",
    val gender: Gender? = null,
    val heightCm: Int = 0,
    val weightKg: Double = 0.0,
    val activityLevel: ActivityLevel? = null,
    val targetCalories: Int = 0,
    val targetProtein: Double = 0.0,
    val targetCarbs: Double = 0.0,
    val targetFat: Double = 0.0
)

/** Aggregates for the Reports screen over a date range. */
data class PeriodReport(
    val start: LocalDate,
    val end: LocalDate,
    val nutritionDays: Int = 0,
    val avgCalories: Int = 0,
    val avgProtein: Int = 0,
    val avgCarbs: Int = 0,
    val avgFat: Int = 0,
    val activityDays: Int = 0,
    val avgSteps: Int = 0,
    val totalExerciseMinutes: Int = 0,
    val totalCaloriesBurned: Int = 0,
    val waterDays: Int = 0,
    val avgWaterMl: Int = 0,
    val completedHabits: Int = 0,
    val activeHabits: Int = 0
)

/** One daily mood check-in (one per date). */
data class MoodEntry(
    val date: LocalDate,
    val mood: String,
    val stress: Int,
    val note: String,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

// ---- Health Connect sync state (moved from data/ so the UI only sees domain types) ----

enum class HcAvailability { AVAILABLE, NEEDS_UPDATE, NOT_SUPPORTED }

data class HcSyncSummary(val days: Int, val sleep: Int, val heartRate: Int, val weight: Int, val bloodPressure: Int, val glucose: Int) {
    val total get() = days + sleep + heartRate + weight + bloodPressure + glucose
}

data class HcState(
    val availability: HcAvailability = HcAvailability.NOT_SUPPORTED,
    val enabled: Boolean = false,
    val granted: Set<String> = emptySet(),
    val syncing: Boolean = false,
    val lastSync: LocalDateTime? = null,
    val lastSummary: HcSyncSummary? = null,
    val error: String? = null,
    /** The device's Health Connect can read while the app is closed. */
    val backgroundSupported: Boolean = false,
    val backgroundGranted: Boolean = false
) {
    val connected get() = enabled && granted.isNotEmpty()
    /** Data is imported every few hours even when the app isn't opened. */
    val backgroundSync get() = connected && backgroundGranted
}

// ---- App preferences ----

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
