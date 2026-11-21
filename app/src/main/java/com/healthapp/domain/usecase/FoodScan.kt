package com.healthapp.domain.usecase

import com.healthapp.domain.model.RecognizedFood
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One AI-estimated food the user can review. The original estimate is kept so changing the
 * portion rescales nutrition proportionally (per-gram rates stay fixed).
 */
data class ScannedFood(
    val name: String,
    val grams: Int,
    val confidence: Float,
    private val baseGrams: Int,
    private val baseCalories: Int,
    private val baseProtein: Double,
    private val baseCarbs: Double,
    private val baseFat: Double,
    /** Calories from the food-composition table (true) or the model's own estimate (false). */
    val fromTable: Boolean = false
) {
    private val factor get() = grams.toDouble() / baseGrams
    val calories: Int get() = (baseCalories * factor).roundToInt()
    val protein: Double get() = round1(baseProtein * factor)
    val carbs: Double get() = round1(baseCarbs * factor)
    val fat: Double get() = round1(baseFat * factor)

    /** Energy implied by macros (Atwater 4/4/9). */
    val caloriesFromMacros: Int get() = (protein * 4 + carbs * 4 + fat * 9).roundToInt()

    /**
     * True when stated calories and macros disagree by >30% (and >40 kcal) — a sign the model
     * mis-estimated one of them. Shown to the user as "kiểm tra lại".
     */
    val energyMismatch: Boolean get() {
        val a = calories; val b = caloriesFromMacros
        if (a == 0 && b == 0) return false
        val diff = abs(a - b)
        return diff > 40 && diff > 0.3 * max(a, b)
    }

    val lowConfidence: Boolean get() = confidence < FoodScan.LOW_CONFIDENCE

    fun withGrams(newGrams: Int): ScannedFood = copy(grams = newGrams.coerceIn(1, FoodScan.MAX_GRAMS))

    companion object {
        private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
    }
}

data class ScanTotals(val calories: Int, val protein: Double, val carbs: Double, val fat: Double)

object FoodScan {
    const val LOW_CONFIDENCE = 0.5f
    const val MAX_GRAMS = 3000

    /** Turns raw AI items into reviewable foods, dropping anything unusable. */
    fun fromRecognized(items: List<RecognizedFood>): List<ScannedFood> = items.mapNotNull { f ->
        val grams = f.estimatedWeightGrams ?: return@mapNotNull null
        if (f.name.isBlank() || grams !in 1..MAX_GRAMS || f.calories !in 0..5000) return@mapNotNull null
        if (listOf(f.protein, f.carbohydrates, f.fat).any { !it.isFinite() || it < 0 }) return@mapNotNull null
        ScannedFood(
            name = f.name.trim(), grams = grams, confidence = f.confidence.coerceIn(0f, 1f),
            baseGrams = grams, baseCalories = f.calories, baseProtein = f.protein, baseCarbs = f.carbohydrates, baseFat = f.fat,
            fromTable = f.caloriesFromTable
        )
    }

    fun totals(items: List<ScannedFood>) = ScanTotals(
        calories = items.sumOf { it.calories },
        protein = items.sumOf { it.protein },
        carbs = items.sumOf { it.carbs },
        fat = items.sumOf { it.fat }
    )
}
