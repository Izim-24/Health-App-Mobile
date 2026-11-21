package com.healthapp.domain.usecase

import com.healthapp.domain.model.DoseLog
import com.healthapp.domain.model.Medication
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

data class BpReading(val at: LocalDateTime, val systolic: Int, val diastolic: Int)
data class WeightReading(val at: LocalDateTime, val kg: Double)
data class HabitHistory(val createdOn: LocalDate, val completedOn: Set<LocalDate>)

/** Raw records needed to score any 7-day window. Loaded once, then scored for many windows. */
data class ScoreData(
    val sleepMinutesByDate: Map<LocalDate, Int> = emptyMap(),
    val stepsByDate: Map<LocalDate, Int> = emptyMap(),
    val bloodPressure: List<BpReading> = emptyList(),
    val weights: List<WeightReading> = emptyList(),
    val profileWeightKg: Double? = null,
    val heightCm: Int? = null,
    val habits: List<HabitHistory> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val doseLogs: List<DoseLog> = emptyList()
)

data class WeekScore(val weekEnd: LocalDate, val input: HealthScoreInput, val result: HealthScoreResult) {
    val score: Int? get() = result.score
}

data class ScoreInsight(val text: String, val trend: Trend) {
    enum class Trend { UP, DOWN, INFO }
}

/**
 * Scores rolling 7-day windows with the same rules as today's [HealthScore], so the weekly history
 * and insights are directly comparable with the number on the dashboard.
 */
object ScoreHistory {
    const val BP_VALID_DAYS = 30L

    /** Summarises the 7 days ending on [end] (inclusive). [now] marks what is still in the future. */
    fun inputFor(data: ScoreData, end: LocalDate, now: LocalDateTime): HealthScoreInput {
        val start = end.minusDays(6)
        val today = now.toLocalDate()
        fun inWindow(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)

        val nights = data.sleepMinutesByDate.filter { (d, m) -> inWindow(d) && m > 0 }.values

        // Steps: today is partial, so the current window uses the 7 full days before it (today only if
        // it is the only data). A past window is complete and uses its own 7 days.
        val steps = data.stepsByDate.filterValues { it > 0 }
        val stepDays = if (!end.isBefore(today)) {
            (1L..7L).mapNotNull { steps[today.minusDays(it)] }.ifEmpty { listOfNotNull(steps[today]) }
        } else {
            steps.filterKeys { inWindow(it) }.values.toList()
        }

        val endOfWindow = end.plusDays(1).atStartOfDay()
        val bp = data.bloodPressure
            .filter { it.at.isBefore(endOfWindow) && it.at.toLocalDate().isAfter(end.minusDays(BP_VALID_DAYS)) }
            .maxByOrNull { it.at }

        val weight = data.weights.filter { it.at.isBefore(endOfWindow) }.maxByOrNull { it.at }?.kg
            ?: data.profileWeightKg?.takeIf { it > 0 }
        val bmi = data.heightCm?.takeIf { it > 0 }?.let { h -> weight?.let { HealthCalculations.bmi(h, it) } }

        // Habits: only days each habit existed count; today counts only once it is completed.
        var habitDone = 0
        var habitPossible = 0
        data.habits.filter { !it.createdOn.isAfter(end) }.forEach { habit ->
            val from = maxOf(start, habit.createdOn)
            var d = from
            while (!d.isAfter(end)) {
                val done = d in habit.completedOn
                if (done) habitDone++
                if (done || d.isBefore(today)) habitPossible++
                d = d.plusDays(1)
            }
        }

        val adherence = data.medications.takeIf { it.isNotEmpty() }
            ?.let { MedicationSchedule.adherence(it, data.doseLogs, start, end, now) }

        return HealthScoreInput(
            avgSleepMinutes = nights.takeIf { it.isNotEmpty() }?.average(), sleepNights = nights.size,
            avgDailySteps = stepDays.takeIf { it.isNotEmpty() }?.average(), stepDays = stepDays.size,
            bmi = bmi,
            systolic = bp?.systolic, diastolic = bp?.diastolic,
            habitCompletedDays = habitDone, habitPossibleDays = habitPossible,
            medsTaken = adherence?.taken ?: 0, medsDue = adherence?.scheduled ?: 0
        )
    }

    /** One score per week, oldest first; the last entry is the current 7 days. */
    fun weekly(data: ScoreData, now: LocalDateTime, weeks: Int = 8): List<WeekScore> {
        val today = now.toLocalDate()
        return (weeks - 1 downTo 0).map { i ->
            val end = today.minusWeeks(i.toLong())
            val input = inputFor(data, end, now)
            WeekScore(end, input, HealthScore.calculate(input))
        }
    }

    /** Plain-language comparison of this week with the previous one, most notable first. */
    fun insights(current: WeekScore, previous: WeekScore?): List<ScoreInsight> {
        val out = mutableListOf<ScoreInsight>()
        val cur = current.input
        val prev = previous?.input

        if (current.score != null && previous?.score != null) {
            val diff = current.score!! - previous.score!!
            out += when {
                diff >= 3 -> ScoreInsight("Điểm tăng $diff so với 7 ngày trước đó.", ScoreInsight.Trend.UP)
                diff <= -3 -> ScoreInsight("Điểm giảm ${-diff} so với 7 ngày trước đó.", ScoreInsight.Trend.DOWN)
                else -> ScoreInsight("Điểm ổn định so với 7 ngày trước đó.", ScoreInsight.Trend.INFO)
            }
        }

        if (prev != null) {
            val sleepNow = cur.avgSleepMinutes
            val sleepBefore = prev.avgSleepMinutes
            if (sleepNow != null && sleepBefore != null) {
                val dh = (sleepNow - sleepBefore) / 60.0
                if (abs(dh) >= 0.3) out += ScoreInsight(
                    "Ngủ ${if (dh > 0) "nhiều" else "ít"} hơn ${"%.1f".format(abs(dh))} giờ/đêm (${"%.1f".format(sleepNow / 60)} giờ).",
                    // More sleep is only better while it moves toward the 7–9 h range, so compare guideline scores.
                    trendOf(HealthScore.sleepScore(sleepNow) - HealthScore.sleepScore(sleepBefore))
                )
            }
            val stepsNow = cur.avgDailySteps
            val stepsBefore = prev.avgDailySteps
            if (stepsNow != null && stepsBefore != null) {
                val ds = (stepsNow - stepsBefore).roundToInt()
                if (abs(ds) >= 500) out += ScoreInsight(
                    "${if (ds > 0) "Nhiều" else "Ít"} hơn ${"%,d".format(abs(ds)).replace(',', '.')} bước/ngày so với tuần trước.",
                    if (ds > 0) ScoreInsight.Trend.UP else ScoreInsight.Trend.DOWN
                )
            }
            rate(cur.medsTaken, cur.medsDue)?.let { now ->
                rate(prev.medsTaken, prev.medsDue)?.let { before ->
                    val d = now - before
                    if (abs(d) >= 10) out += ScoreInsight(
                        "Uống thuốc đúng lịch ${if (d > 0) "tốt hơn" else "kém hơn"}: $now% (tuần trước $before%).",
                        if (d > 0) ScoreInsight.Trend.UP else ScoreInsight.Trend.DOWN
                    )
                }
            }
            rate(cur.habitCompletedDays, cur.habitPossibleDays)?.let { now ->
                rate(prev.habitCompletedDays, prev.habitPossibleDays)?.let { before ->
                    val d = now - before
                    if (abs(d) >= 10) out += ScoreInsight(
                        "Hoàn thành thói quen $now% (tuần trước $before%).",
                        if (d > 0) ScoreInsight.Trend.UP else ScoreInsight.Trend.DOWN
                    )
                }
            }
        }

        val parts = current.result.components
        if (parts.size >= 2) {
            val best = parts.maxBy { it.score }
            val worst = parts.minBy { it.score }
            if (worst.score < 100 && best.area != worst.area) out += ScoreInsight(
                "Mạnh nhất: ${best.area.label} (${best.score}). Nên ưu tiên: ${worst.area.label} (${worst.score}).",
                ScoreInsight.Trend.INFO
            )
        }
        return out
    }

    private fun trendOf(delta: Int) = when {
        delta > 0 -> ScoreInsight.Trend.UP
        delta < 0 -> ScoreInsight.Trend.DOWN
        else -> ScoreInsight.Trend.INFO
    }

    private fun rate(done: Int, total: Int): Int? = if (total > 0) (done * 100.0 / total).roundToInt() else null
}
