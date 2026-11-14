package com.healthapp.domain.usecase

import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthCalculationsTest {

    @Test fun bmi_isWeightOverHeightSquared() {
        assertEquals(22.86, HealthCalculations.bmi(175, 70.0)!!, 0.01)
    }

    @Test fun bmi_nullForMissingInput() {
        assertNull(HealthCalculations.bmi(0, 70.0))
        assertNull(HealthCalculations.bmi(170, 0.0))
    }

    @Test fun bmiCategory_asiaPacificBoundaries_matchDisplayedValue() {
        assertEquals(BmiCategory.SEVERELY_UNDERWEIGHT, HealthCalculations.bmiCategory(16.9))
        assertEquals(BmiCategory.UNDERWEIGHT, HealthCalculations.bmiCategory(18.4))
        assertEquals(BmiCategory.NORMAL, HealthCalculations.bmiCategory(18.5))
        assertEquals(BmiCategory.NORMAL, HealthCalculations.bmiCategory(22.9))
        assertEquals(BmiCategory.OVERWEIGHT, HealthCalculations.bmiCategory(23.0))
        assertEquals(BmiCategory.OVERWEIGHT, HealthCalculations.bmiCategory(22.96)) // shown as "23.0"
        assertEquals(BmiCategory.OBESE_1, HealthCalculations.bmiCategory(25.0))
        assertEquals(BmiCategory.OBESE_2, HealthCalculations.bmiCategory(30.0))
    }

    @Test fun age_countsCompletedYearsOnly() {
        val today = LocalDate.of(2026, 11, 14)
        assertEquals(30, HealthCalculations.ageOn(LocalDate.of(1996, 11, 14), today))
        assertEquals(29, HealthCalculations.ageOn(LocalDate.of(1996, 11, 15), today))
        assertNull(HealthCalculations.ageOn(LocalDate.of(2027, 1, 1), today))
    }

    @Test fun bmr_mifflinStJeor() {
        // 10*70 + 6.25*175 - 5*30 = 1643.75
        assertEquals(1648.75, HealthCalculations.bmr(Gender.MALE, 70.0, 175, 30), 0.001)
        assertEquals(1482.75, HealthCalculations.bmr(Gender.FEMALE, 70.0, 175, 30), 0.001)
        assertEquals(1565.75, HealthCalculations.bmr(Gender.OTHER, 70.0, 175, 30), 0.001)
    }

    @Test fun energyTargets_applyActivityAndMacroSplit() {
        val t = HealthCalculations.energyTargets(Gender.MALE, 70.0, 175, 30, ActivityLevel.MODERATELY_ACTIVE)!!
        assertEquals(1649, t.bmr)
        assertEquals(2556, t.tdee) // 1648.75 * 1.55
        assertEquals(112.0, t.proteinGrams, 0.05) // 1.6 g/kg
        assertEquals(85.2, t.fatGrams, 0.05) // 30% of energy
        val energy = t.proteinGrams * 4 + t.carbsGrams * 4 + t.fatGrams * 9
        assertEquals(t.tdee.toDouble(), energy, 2.0)
    }

    @Test fun energyTargets_rejectImplausibleInput() {
        assertNull(HealthCalculations.energyTargets(Gender.MALE, 70.0, 175, 10, ActivityLevel.SEDENTARY))
        assertNull(HealthCalculations.energyTargets(Gender.MALE, 70.0, 50, 30, ActivityLevel.SEDENTARY))
    }

    @Test fun suggestedWater_scalesWithWeightWithinBounds() {
        assertEquals(2450, HealthCalculations.suggestedWaterMl(70.0))
        assertEquals(1200, HealthCalculations.suggestedWaterMl(20.0))
        assertEquals(4000, HealthCalculations.suggestedWaterMl(200.0))
        assertNull(HealthCalculations.suggestedWaterMl(0.0))
        assertTrue(HealthCalculations.suggestedWaterMl(71.0)!! % 50 == 0)
    }
}
