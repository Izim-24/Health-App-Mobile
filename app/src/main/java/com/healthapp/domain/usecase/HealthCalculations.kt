package com.healthapp.domain.usecase

import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

/** Daily energy and macro targets derived from the user's own profile data. */
data class EnergyTargets(
    val bmr: Int,
    val tdee: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double
)

/**
 * Adult BMI categories using the WHO Asia-Pacific cut-offs (WHO/IASO/IOTF 2000), which are
 * commonly used in Vietnam: overweight starts at 23 and obesity at 25, lower than the global
 * WHO 25/30. [score] is this category's contribution to the dashboard health score.
 */
enum class BmiCategory(val label: String, val score: Int) {
    SEVERELY_UNDERWEIGHT("thiếu cân nhiều", 40),
    UNDERWEIGHT("thiếu cân", 70),
    NORMAL("bình thường", 100),
    OVERWEIGHT("thừa cân", 80),
    OBESE_1("béo phì độ I", 55),
    OBESE_2("béo phì độ II", 35)
}

/** Pure health formulas used by the profile and dashboard. No Android dependencies. */
object HealthCalculations {

    fun bmi(heightCm: Int, weightKg: Double): Double? {
        if (heightCm <= 0 || weightKg <= 0.0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    const val BMI_NORMAL_RANGE = "18,5–22,9"

    /** Classified on the value rounded to 1 decimal, so the label always matches the displayed BMI. */
    fun bmiCategory(bmi: Double): BmiCategory {
        val b = (bmi * 10).roundToInt() / 10.0
        return when {
            b < 17.0 -> BmiCategory.SEVERELY_UNDERWEIGHT
            b < 18.5 -> BmiCategory.UNDERWEIGHT
            b < 23.0 -> BmiCategory.NORMAL
            b < 25.0 -> BmiCategory.OVERWEIGHT
            b < 30.0 -> BmiCategory.OBESE_1
            else -> BmiCategory.OBESE_2
        }
    }

    fun ageOn(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Int? {
        if (birthDate.isAfter(today)) return null
        return Period.between(birthDate, today).years
    }

    fun activityMultiplier(level: ActivityLevel): Double = when (level) {
        ActivityLevel.SEDENTARY -> 1.2
        ActivityLevel.LIGHTLY_ACTIVE -> 1.375
        ActivityLevel.MODERATELY_ACTIVE -> 1.55
        ActivityLevel.VERY_ACTIVE -> 1.725
        ActivityLevel.EXTRA_ACTIVE -> 1.9
    }

    /**
     * Mifflin–St Jeor basal metabolic rate (kcal/day).
     * For [Gender.OTHER] the midpoint of the male and female constants is used.
     */
    fun bmr(gender: Gender, weightKg: Double, heightCm: Int, ageYears: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears
        return base + when (gender) {
            Gender.MALE -> 5.0
            Gender.FEMALE -> -161.0
            Gender.OTHER -> -78.0
        }
    }

    /**
     * Maintenance targets. Macros: protein 1.6 g/kg (capped at 30% of energy),
     * fat 30% of energy, remaining energy from carbohydrate.
     * Returns null when inputs are missing or out of a plausible range.
     */
    fun energyTargets(
        gender: Gender,
        weightKg: Double,
        heightCm: Int,
        ageYears: Int,
        activityLevel: ActivityLevel
    ): EnergyTargets? {
        if (weightKg !in 20.0..400.0 || heightCm !in 100..250 || ageYears !in 15..110) return null
        val bmr = bmr(gender, weightKg, heightCm, ageYears)
        val tdee = (bmr * activityMultiplier(activityLevel)).roundToInt()
        val protein = minOf(1.6 * weightKg, tdee * 0.30 / 4.0)
        val fat = tdee * 0.30 / 9.0
        val carbs = ((tdee - protein * 4 - fat * 9) / 4.0).coerceAtLeast(0.0)
        return EnergyTargets(
            bmr = bmr.roundToInt(),
            tdee = tdee,
            proteinGrams = protein.round1(),
            carbsGrams = carbs.round1(),
            fatGrams = fat.round1()
        )
    }

    /** Common guideline of ~35 ml per kg body weight, clamped to a sensible range. */
    fun suggestedWaterMl(weightKg: Double): Int? =
        if (weightKg <= 0) null else ((weightKg * 35).roundToInt() / 50 * 50).coerceIn(1200, 4000)

    private fun Double.round1() = (this * 10).roundToInt() / 10.0
}
