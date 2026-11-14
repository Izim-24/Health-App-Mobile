package com.healthapp.domain.usecase

import com.healthapp.domain.model.RecognizedFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodScanTest {
    private fun raw(name: String = "Cơm trắng", grams: Int? = 200, kcal: Int = 260, p: Double = 5.0, c: Double = 57.0, f: Double = 0.6, conf: Float = 0.8f) =
        RecognizedFood(name = name, calories = kcal, protein = p, carbohydrates = c, fat = f, estimatedWeightGrams = grams, confidence = conf)

    @Test fun fromRecognized_dropsUnusableItems() {
        val items = FoodScan.fromRecognized(listOf(raw(), raw(name = " "), raw(grams = null), raw(grams = 0), raw(kcal = -1), raw(p = Double.NaN)))
        assertEquals(1, items.size)
        assertEquals("Cơm trắng", items[0].name)
    }

    @Test fun changingGrams_rescalesNutritionProportionally() {
        val rice = FoodScan.fromRecognized(listOf(raw())).single()
        val half = rice.withGrams(100)
        assertEquals(130, half.calories)
        assertEquals(2.5, half.protein, 0.001)
        assertEquals(28.5, half.carbs, 0.001)
        // Rescaling again is based on the original estimate, so no rounding drift
        assertEquals(260, half.withGrams(200).calories)
    }

    @Test fun withGrams_isClamped() {
        val rice = FoodScan.fromRecognized(listOf(raw())).single()
        assertEquals(1, rice.withGrams(0).grams)
        assertEquals(FoodScan.MAX_GRAMS, rice.withGrams(99_999).grams)
    }

    @Test fun energyMismatch_flagsCaloriesThatDisagreeWithMacros() {
        // 5*4 + 57*4 + 0.6*9 = 253 kcal vs 260 stated → consistent
        assertFalse(FoodScan.fromRecognized(listOf(raw())).single().energyMismatch)
        // 450 kcal stated, macros only explain ~253 → flagged
        assertTrue(FoodScan.fromRecognized(listOf(raw(kcal = 450))).single().energyMismatch)
        // small absolute differences on tiny items are tolerated
        assertFalse(FoodScan.fromRecognized(listOf(raw(grams = 10, kcal = 30, p = 0.0, c = 2.0, f = 0.0))).single().energyMismatch)
    }

    @Test fun totals_sumAllItems() {
        val items = FoodScan.fromRecognized(listOf(raw(), raw(name = "Trứng chiên", grams = 50, kcal = 90, p = 6.0, c = 0.5, f = 7.0)))
        val t = FoodScan.totals(items)
        assertEquals(350, t.calories)
        assertEquals(11.0, t.protein, 0.001)
        assertTrue(items.none { it.lowConfidence })
    }
}
