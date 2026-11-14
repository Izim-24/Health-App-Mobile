package com.healthapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthScoreTest {

    @Test fun sleep_fullMarksIn7to9Hours_taperingOutside() {
        assertEquals(100, HealthScore.sleepScore(7 * 60.0))
        assertEquals(100, HealthScore.sleepScore(9 * 60.0))
        assertEquals(75, HealthScore.sleepScore(6 * 60.0))
        assertEquals(50, HealthScore.sleepScore(5 * 60.0))
        assertEquals(0, HealthScore.sleepScore(2 * 60.0))
        assertEquals(80, HealthScore.sleepScore(10 * 60.0))
    }

    @Test fun steps_linearFrom2000To8000() {
        assertEquals(0, HealthScore.stepsScore(1500.0))
        assertEquals(50, HealthScore.stepsScore(5000.0))
        assertEquals(100, HealthScore.stepsScore(12000.0))
    }

    @Test fun bloodPressure_usesWorseOfSystolicAndDiastolic() {
        assertEquals(100, HealthScore.bloodPressure(118, 76).first)
        assertEquals(80, HealthScore.bloodPressure(125, 78).first)
        assertEquals(60, HealthScore.bloodPressure(118, 84).first) // diastolic alone → stage 1
        assertEquals(35, HealthScore.bloodPressure(142, 70).first)
        assertEquals(10, HealthScore.bloodPressure(185, 95).first)
    }

    @Test fun bmi_asiaPacificCategories() {
        assertEquals(100, HealthScore.bmiScore(22.0).first)
        assertEquals(80, HealthScore.bmiScore(23.1).first)  // overweight in Asia-Pacific, normal under global WHO
        assertEquals(55, HealthScore.bmiScore(27.0).first)
        assertEquals(35, HealthScore.bmiScore(31.0).first)
        assertEquals(70, HealthScore.bmiScore(18.0).first)
        assertEquals(40, HealthScore.bmiScore(15.2).first)
    }

    @Test fun noScoreUntilThreeAreas_andListsMissingOnes() {
        val r = HealthScore.calculate(HealthScoreInput(avgSleepMinutes = 450.0, sleepNights = 1, bmi = 22.0))
        assertNull(r.score)
        assertEquals(2, r.components.size)
        assertEquals(listOf(ScoreArea.ACTIVITY, ScoreArea.BLOOD_PRESSURE), r.missing)
    }

    @Test fun weightedAverageOverAvailableAreas() {
        // sleep 100 (w20), steps 50 (w20), BMI 40 (w15) → (2000 + 1000 + 600) / 55 = 65.45 → 65
        val r = HealthScore.calculate(HealthScoreInput(
            avgSleepMinutes = 480.0, sleepNights = 3, avgDailySteps = 5000.0, stepDays = 3, bmi = 15.2
        ))
        assertEquals(65, r.score)
        assertEquals("Cần cải thiện", r.grade)
        assertEquals(ScoreArea.BMI, r.topTip?.area) // weakest area with a tip
    }

    @Test fun medicationAndHabitsCountOnlyWhenUsed_neverMissing() {
        val base = HealthScoreInput(avgSleepMinutes = 480.0, sleepNights = 7, avgDailySteps = 9000.0, stepDays = 7, systolic = 115, diastolic = 75)
        val r = HealthScore.calculate(base)
        assertEquals(100, r.score)
        assertTrue(ScoreArea.MEDICATION !in r.missing && ScoreArea.HABITS !in r.missing)
        assertNull(r.topTip)
        // Adding 50% medication adherence (w15) pulls it down: (6000 + 750) / 75 = 90
        assertEquals(90, HealthScore.calculate(base.copy(medsTaken = 7, medsDue = 14)).score)
    }
}
