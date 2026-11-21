package com.healthapp.domain.usecase

import kotlin.math.roundToInt

/** Areas of the score and their weights. The UI supplies the names (string resources). */
enum class ScoreArea(val weight: Int) {
    SLEEP(20),
    ACTIVITY(20),
    BLOOD_PRESSURE(20),
    BMI(15),
    MEDICATION(15),
    HABITS(10)
}

/** ACC/AHA 2017 blood-pressure categories with their score. */
enum class BpCategory(val score: Int) { NORMAL(100), ELEVATED(80), STAGE_1(60), STAGE_2(35), CRISIS(10) }

enum class ScoreGrade { EXCELLENT, GOOD, FAIR, POOR }

/** What an area's score is based on; formatted by the UI. */
sealed interface ScoreDetail {
    data class Sleep(val avgHours: Double, val nights: Int) : ScoreDetail
    data class Activity(val avgSteps: Int, val days: Int) : ScoreDetail
    data class BloodPressure(val systolic: Int, val diastolic: Int, val category: BpCategory) : ScoreDetail
    data class Bmi(val bmi: Double, val category: BmiCategory) : ScoreDetail
    data class Medication(val taken: Int, val due: Int) : ScoreDetail
    data class Habits(val done: Int, val possible: Int) : ScoreDetail
}

/** Advice shown when an area has room to improve. */
enum class ScoreTip { SLEEP_MORE, SLEEP_LESS, MORE_STEPS, BP_SEE_DOCTOR, BP_LIFESTYLE, BMI_RANGE, MED_REMINDERS, FEWER_HABITS }

data class ScoreComponent(
    val area: ScoreArea,
    val score: Int,            // 0..100
    val detail: ScoreDetail,   // what the score is based on
    val tip: ScoreTip?         // shown when there is room to improve
)

data class HealthScoreResult(
    val score: Int?,                       // null until enough areas have data
    val components: List<ScoreComponent>,
    val missing: List<ScoreArea>
) {
    val grade: ScoreGrade? get() = score?.let {
        when {
            it >= 85 -> ScoreGrade.EXCELLENT
            it >= 70 -> ScoreGrade.GOOD
            it >= 50 -> ScoreGrade.FAIR
            else -> ScoreGrade.POOR
        }
    }
    /** The weakest area with a tip, to surface on the dashboard. */
    val topTip: ScoreComponent? get() = components.filter { it.tip != null }.minByOrNull { it.score }
}

/** Inputs, each already summarised over its window. Null = no data for that area. */
data class HealthScoreInput(
    val avgSleepMinutes: Double? = null, val sleepNights: Int = 0,             // last 7 nights
    val avgDailySteps: Double? = null, val stepDays: Int = 0,                   // recent full days
    val bmi: Double? = null,
    val systolic: Int? = null, val diastolic: Int? = null,                     // latest within 30 days
    val habitCompletedDays: Int = 0, val habitPossibleDays: Int = 0,            // summed over active habits
    val medsTaken: Int = 0, val medsDue: Int = 0                                // last 7 days
)

/**
 * A transparent wellness score: each area is scored 0–100 against a public guideline, then
 * averaged by weight over the areas that have data. Not a medical assessment or diagnosis.
 *
 *  - Sleep: 7–9 h/night for adults (AASM / National Sleep Foundation).
 *  - Activity: ~8,000 steps/day, where mortality benefit largely plateaus for adults
 *    (Paluch et al., Lancet Public Health 2022); 2,000 steps/day or less scores 0.
 *  - Blood pressure: ACC/AHA 2017 categories; the worse of systolic/diastolic counts.
 *  - BMI: WHO Asia-Pacific categories (normal 18.5–22.9), see [BmiCategory].
 *  - Medication: share of due doses marked taken (≥80% is the usual "adherent" threshold).
 *  - Habits: share of possible habit-days completed.
 */
object HealthScore {
    const val MIN_AREAS = 3

    fun sleepScore(avgMinutes: Double): Int {
        val h = avgMinutes / 60.0
        val s = when {
            h < 7 -> 100 - (7 - h) * 25      // 6h → 75, 5h → 50, 3h → 0
            h <= 9 -> 100.0
            else -> 100 - (h - 9) * 20       // 10h → 80
        }
        return s.roundToInt().coerceIn(0, 100)
    }

    fun stepsScore(avgSteps: Double): Int = ((avgSteps - 2000) / (8000 - 2000) * 100).roundToInt().coerceIn(0, 100)

    /** ACC/AHA 2017 category; the worse of systolic/diastolic counts. */
    fun bpCategory(sys: Int, dia: Int): BpCategory = when {
        sys > 180 || dia > 120 -> BpCategory.CRISIS
        sys >= 140 || dia >= 90 -> BpCategory.STAGE_2
        sys >= 130 || dia >= 80 -> BpCategory.STAGE_1
        sys >= 120 -> BpCategory.ELEVATED
        else -> BpCategory.NORMAL
    }

    fun bloodPressure(sys: Int, dia: Int): Pair<Int, BpCategory> = bpCategory(sys, dia).let { it.score to it }

    fun bmiScore(bmi: Double): Pair<Int, BmiCategory> =
        HealthCalculations.bmiCategory(bmi).let { it.score to it }

    fun calculate(input: HealthScoreInput): HealthScoreResult {
        val parts = mutableListOf<ScoreComponent>()

        input.avgSleepMinutes?.takeIf { input.sleepNights > 0 }?.let { m ->
            val s = sleepScore(m)
            val h = m / 60
            parts += ScoreComponent(ScoreArea.SLEEP, s, ScoreDetail.Sleep(h, input.sleepNights),
                when { h < 7 -> ScoreTip.SLEEP_MORE; h > 9 -> ScoreTip.SLEEP_LESS; else -> null })
        }
        input.avgDailySteps?.takeIf { input.stepDays > 0 }?.let { avg ->
            val s = stepsScore(avg)
            parts += ScoreComponent(ScoreArea.ACTIVITY, s, ScoreDetail.Activity(avg.roundToInt(), input.stepDays),
                if (s < 100) ScoreTip.MORE_STEPS else null)
        }
        if (input.systolic != null && input.diastolic != null) {
            val (s, category) = bloodPressure(input.systolic, input.diastolic)
            parts += ScoreComponent(ScoreArea.BLOOD_PRESSURE, s, ScoreDetail.BloodPressure(input.systolic, input.diastolic, category),
                when { s <= 35 -> ScoreTip.BP_SEE_DOCTOR; s < 100 -> ScoreTip.BP_LIFESTYLE; else -> null })
        }
        input.bmi?.takeIf { it > 0 }?.let { bmi ->
            val (s, category) = bmiScore(bmi)
            parts += ScoreComponent(ScoreArea.BMI, s, ScoreDetail.Bmi(bmi, category), if (s < 100) ScoreTip.BMI_RANGE else null)
        }
        if (input.medsDue > 0) {
            val rate = input.medsTaken.toDouble() / input.medsDue
            parts += ScoreComponent(ScoreArea.MEDICATION, (rate * 100).roundToInt().coerceIn(0, 100),
                ScoreDetail.Medication(input.medsTaken, input.medsDue), if (rate < 0.8) ScoreTip.MED_REMINDERS else null)
        }
        if (input.habitPossibleDays > 0) {
            val rate = input.habitCompletedDays.toDouble() / input.habitPossibleDays
            parts += ScoreComponent(ScoreArea.HABITS, (rate * 100).roundToInt().coerceIn(0, 100),
                ScoreDetail.Habits(input.habitCompletedDays, input.habitPossibleDays), if (rate < 0.7) ScoreTip.FEWER_HABITS else null)
        }

        val present = parts.map { it.area }.toSet()
        // Medication and habits only count if the user uses those features, so they're never "missing".
        val missing = listOf(ScoreArea.SLEEP, ScoreArea.ACTIVITY, ScoreArea.BLOOD_PRESSURE, ScoreArea.BMI).filter { it !in present }
        val score = if (parts.size >= MIN_AREAS) {
            val totalWeight = parts.sumOf { it.area.weight }
            (parts.sumOf { it.score * it.area.weight }.toDouble() / totalWeight).roundToInt()
        } else null
        return HealthScoreResult(score, parts.sortedBy { it.area.ordinal }, missing)
    }
}
