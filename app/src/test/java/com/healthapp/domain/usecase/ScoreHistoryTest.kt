package com.healthapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScoreHistoryTest {
    private val today = LocalDate.of(2026, 11, 22)
    private val now = today.atTime(LocalTime.of(21, 0))

    private fun days(from: Long, to: Long, value: Int) = (from..to).associate { today.minusDays(it) to value }

    @Test
    fun `current window uses the 7 full days before today for steps`() {
        val data = ScoreData(stepsByDate = days(1, 7, 8000) + (today to 500))
        val input = ScoreHistory.inputFor(data, today, now)
        assertEquals(8000.0, input.avgDailySteps!!, 0.01)
        assertEquals(7, input.stepDays)
    }

    @Test
    fun `past window uses only its own days`() {
        val data = ScoreData(
            sleepMinutesByDate = days(0, 6, 480) + days(7, 13, 360),
            stepsByDate = days(7, 13, 4000) + days(0, 6, 9000)
        )
        val lastWeek = ScoreHistory.inputFor(data, today.minusDays(7), now)
        assertEquals(360.0, lastWeek.avgSleepMinutes!!, 0.01)
        assertEquals(7, lastWeek.sleepNights)
        assertEquals(4000.0, lastWeek.avgDailySteps!!, 0.01)
    }

    @Test
    fun `blood pressure older than 30 days before the window end is ignored`() {
        val reading = BpReading(today.minusDays(40).atTime(8, 0), 150, 95)
        val data = ScoreData(bloodPressure = listOf(reading))
        assertNull(ScoreHistory.inputFor(data, today, now).systolic)
        assertEquals(150, ScoreHistory.inputFor(data, today.minusDays(14), now).systolic)
    }

    @Test
    fun `bmi uses the latest weight up to the window end`() {
        val data = ScoreData(
            heightCm = 170,
            weights = listOf(WeightReading(today.minusDays(20).atTime(7, 0), 80.0), WeightReading(today.minusDays(1).atTime(7, 0), 66.0))
        )
        assertEquals(66.0 / (1.7 * 1.7), ScoreHistory.inputFor(data, today, now).bmi!!, 0.01)
        assertEquals(80.0 / (1.7 * 1.7), ScoreHistory.inputFor(data, today.minusDays(7), now).bmi!!, 0.01)
    }

    @Test
    fun `habits count only existing days and today only once done`() {
        val habit = HabitHistory(createdOn = today.minusDays(2), completedOn = setOf(today.minusDays(2), today.minusDays(1)))
        val input = ScoreHistory.inputFor(ScoreData(habits = listOf(habit)), today, now)
        assertEquals(2, input.habitCompletedDays)
        assertEquals(2, input.habitPossibleDays) // today not done yet → not counted as missed
    }

    @Test
    fun `weekly returns oldest first and ends with the current window`() {
        val data = ScoreData(sleepMinutesByDate = days(0, 60, 480), stepsByDate = days(0, 60, 8000), heightCm = 165, profileWeightKg = 58.0)
        val weeks = ScoreHistory.weekly(data, now, 8)
        assertEquals(8, weeks.size)
        assertEquals(today, weeks.last().weekEnd)
        assertEquals(today.minusWeeks(7), weeks.first().weekEnd)
        assertTrue(weeks.all { it.score != null })
    }

    @Test
    fun `insights report score and sleep change`() {
        val data = ScoreData(
            sleepMinutesByDate = days(0, 6, 450) + days(7, 13, 330),
            stepsByDate = days(0, 13, 8000),
            heightCm = 165, profileWeightKg = 58.0
        )
        val weeks = ScoreHistory.weekly(data, now, 2)
        val insights = ScoreHistory.insights(weeks[1], weeks[0])
        val change = insights.first() as ScoreInsight.ScoreChange
        assertTrue(change.diff >= 3)
        assertEquals(ScoreInsight.Trend.UP, change.trend)
        val sleep = insights.filterIsInstance<ScoreInsight.SleepChange>().single()
        assertEquals(2.0, sleep.deltaHours, 0.01)
        assertEquals(ScoreInsight.Trend.UP, sleep.trend)
    }

    @Test
    fun `sleeping more than 9 hours is not reported as an improvement`() {
        val data = ScoreData(sleepMinutesByDate = days(0, 6, 660) + days(7, 13, 480))
        val weeks = ScoreHistory.weekly(data, now, 2)
        val sleep = ScoreHistory.insights(weeks[1], weeks[0]).filterIsInstance<ScoreInsight.SleepChange>().single()
        assertTrue(sleep.deltaHours > 0)
        assertEquals(ScoreInsight.Trend.DOWN, sleep.trend)
    }

    @Test
    fun `no previous week gives only the strongest and weakest area`() {
        val data = ScoreData(sleepMinutesByDate = days(0, 6, 480), stepsByDate = days(1, 7, 4000))
        val current = ScoreHistory.weekly(data, LocalDateTime.of(today, LocalTime.NOON), 1).single()
        val insights = ScoreHistory.insights(current, null)
        assertEquals(1, insights.size)
        assertEquals(ScoreInsight.Focus(ScoreArea.SLEEP, 100, ScoreArea.ACTIVITY, 33), insights.single())
    }
}
