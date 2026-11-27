package com.healthapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class VitalAlertsTest {
    private val t0: LocalDateTime = LocalDateTime.of(2026, 11, 28, 9, 0)

    /** One heart-rate sample per minute, starting [fromMin] minutes after t0. */
    private fun hr(minutes: Int, bpm: Int, fromMin: Int = 0) = (0 until minutes).map { HrSample(t0.plusMinutes((fromMin + it).toLong()), bpm) }
    private fun steps(minutes: Int, perMin: Int, fromMin: Int = 0) = (0 until minutes).map { StepSample(t0.plusMinutes((fromMin + it).toLong()), perMin) }
    private fun kinds(a: List<VitalAlert>) = a.map { it.kind }
    private fun nowAfter(samples: List<HrSample>) = samples.last().time.plusMinutes(1)
    /** The watch reporting 0 steps in each of these minutes (it is worn and the person is still). */
    private fun still(samples: List<HrSample>) = samples.map { StepSample(it.time, 0) }

    @Test fun maxHeartRate_usesAgeOr40() {
        assertEquals(192, VitalAlerts.maxHeartRate(28))
        assertEquals(180, VitalAlerts.maxHeartRate(null))
    }

    @Test fun activityState_activeRestingRecovering() {
        val at = t0.plusMinutes(30)
        assertEquals(ActivityState.ACTIVE, VitalAlerts.activityState(steps(10, 60, fromMin = 21), at))
        assertEquals(ActivityState.RESTING, VitalAlerts.activityState(emptyList(), at))
        assertEquals(ActivityState.RECOVERING, VitalAlerts.activityState(steps(10, 60, fromMin = 11), at))
    }

    @Test fun restTachy_after10min_warning() {
        val s = hr(12, 125)
        val a = VitalAlerts.heartAlerts(s, still(s), 28, nowAfter(s))
        assertEquals(listOf(AlertKind.REST_TACHY), kinds(a))
        assertEquals(125, a.single().value)
        assertEquals(ActivityState.RESTING, a.single().state)
        assertEquals(AlertLevel.WARNING, a.single().level)
    }

    @Test fun restTachy_9min_noAlert() {
        val s = hr(9, 125)
        assertTrue(VitalAlerts.heartAlerts(s, still(s), 28, nowAfter(s)).isEmpty())
    }

    @Test fun restTachySevere_supersedesWarning() {
        val s = hr(6, 155)
        assertEquals(listOf(AlertKind.REST_TACHY_SEVERE), kinds(VitalAlerts.heartAlerts(s, still(s), 28, nowAfter(s))))
    }

    @Test fun restBrady_after10min() {
        val s = hr(11, 38)
        assertEquals(listOf(AlertKind.REST_BRADY), kinds(VitalAlerts.heartAlerts(s, still(s), 28, nowAfter(s))))
    }

    @Test fun running_highHr_noAlert() {
        val s = (0 until 20).map { HrSample(t0.plusMinutes(it.toLong()), 155 + it % 11) }
        assertTrue(VitalAlerts.heartAlerts(s, steps(20, 150), 28, nowAfter(s)).isEmpty())
    }

    @Test fun overExertion_after5min() {
        val s = hr(6, 180)
        val a = VitalAlerts.heartAlerts(s, steps(16, 150, fromMin = -10), 28, nowAfter(s)) // already running for 10 min
        assertEquals(listOf(AlertKind.OVER_EXERTION), kinds(a))
        assertEquals(ActivityState.ACTIVE, a.single().state)
        assertEquals(192, a.single().secondary) // max heart rate, shown in the notification
    }

    @Test fun aboveMax_supersedesOverExertion() {
        val s = hr(3, 195)
        assertEquals(listOf(AlertKind.ABOVE_MAX_HR), kinds(VitalAlerts.heartAlerts(s, steps(13, 200, fromMin = -10), 28, nowAfter(s))))
    }

    @Test fun recoveryAfterRun_noRestAlert() {
        val s = hr(15, 150) + hr(6, 130, fromMin = 15)
        assertTrue(VitalAlerts.heartAlerts(s, steps(15, 150), 28, nowAfter(s)).isEmpty())
    }

    @Test fun dataGap_breaksSustained() {
        val s = hr(5, 125) + hr(5, 125, fromMin = 25)
        assertTrue(VitalAlerts.heartAlerts(s, still(s), 28, nowAfter(s)).isEmpty())
    }

    @Test fun oldData_ignored() {
        val s = hr(15, 125)
        assertTrue(VitalAlerts.heartAlerts(s, still(s), 28, s.last().time.plusHours(3)).isEmpty())
    }

    @Test fun bpCrisis_and_high() {
        val now = t0.plusMinutes(5)
        fun one(sys: Int, dia: Int) = kinds(VitalAlerts.bpAlerts(listOf(BpReading(t0, sys, dia)), now))
        assertEquals(listOf(AlertKind.BP_CRISIS), one(185, 100))
        assertEquals(listOf(AlertKind.BP_CRISIS), one(150, 122))
        assertEquals(listOf(AlertKind.BP_HIGH), one(165, 95))
        assertEquals(emptyList<AlertKind>(), one(150, 95))
    }

    @Test fun bpSustained_needs5Readings() {
        val now = t0
        val week = listOf(136 to 80, 140 to 84, 138 to 82, 137 to 81, 139 to 83).mapIndexed { i, (s, d) -> BpReading(t0.minusDays(i + 1L), s, d) }
        val a = VitalAlerts.bpAlerts(week, now)
        assertEquals(listOf(AlertKind.BP_SUSTAINED), kinds(a))
        assertEquals(138, a.single().value)
        assertEquals(82, a.single().secondary)
        assertTrue(VitalAlerts.bpAlerts(week.take(4), now).isEmpty())
        val oneTooOld = week.take(4) + BpReading(t0.minusDays(8), 140, 84)
        assertTrue(VitalAlerts.bpAlerts(oneTooOld, now).isEmpty())
    }

    @Test fun dueAlerts_cooldown30min_trendOncePerDay() {
        val now = t0.plusHours(3)
        val tachy = VitalAlert(AlertKind.REST_TACHY, now, 125)
        val trend = VitalAlert(AlertKind.BP_SUSTAINED, now, 138, 82)
        assertTrue(VitalAlerts.dueAlerts(listOf(tachy), mapOf(AlertKind.REST_TACHY to now.minusMinutes(20)), now).isEmpty())
        assertEquals(listOf(tachy), VitalAlerts.dueAlerts(listOf(tachy), mapOf(AlertKind.REST_TACHY to now.minusMinutes(31)), now))
        assertEquals(listOf(tachy), VitalAlerts.dueAlerts(listOf(tachy), emptyMap(), now))
        assertTrue(VitalAlerts.dueAlerts(listOf(trend), mapOf(AlertKind.BP_SUSTAINED to now.minusHours(2)), now).isEmpty())
        assertEquals(listOf(trend), VitalAlerts.dueAlerts(listOf(trend), mapOf(AlertKind.BP_SUSTAINED to now.minusDays(1)), now))
    }

    // ---- review fixes ----

    @Test fun restRules_skippedWithoutAnyStepData() {
        // Cycling, gym or no steps permission: no step data at all, so "resting" can't be told apart from exercise.
        val s = hr(12, 155)
        assertTrue(VitalAlerts.heartAlerts(s, emptyList(), 28, nowAfter(s)).isEmpty())
    }

    @Test fun dueAlerts_sameReadingNeverRealerted() {
        val reading = t0
        val crisis = VitalAlert(AlertKind.BP_CRISIS, reading, 185, 120)
        val now = reading.plusMinutes(31)
        val lastSent = mapOf(AlertKind.BP_CRISIS to reading)
        val lastAt = mapOf(AlertKind.BP_CRISIS to reading)
        assertTrue(VitalAlerts.dueAlerts(listOf(crisis), lastSent, now, lastAt).isEmpty())
        val newer = crisis.copy(at = reading.plusMinutes(5))
        assertEquals(listOf(newer), VitalAlerts.dueAlerts(listOf(newer), lastSent, now, lastAt))
    }

    @Test fun isFresh_onlyRecentReadingsNotify() {
        val a = VitalAlert(AlertKind.REST_TACHY, t0, 125)
        assertTrue(VitalAlerts.isFresh(a, t0.plusMinutes(15)))
        assertFalse(VitalAlerts.isFresh(a, t0.plusMinutes(16)))
    }

    @Test fun singleHeartReading_veryFastOnly() {
        assertEquals(AlertKind.REST_TACHY_SEVERE, VitalAlerts.singleHeartReading(HrSample(t0, 170))?.kind)
        assertEquals(null, VitalAlerts.singleHeartReading(HrSample(t0, 120)))
    }

    @Test fun simulatedDue_oncePerKindPerRun() {
        val a = VitalAlert(AlertKind.REST_TACHY, t0, 125)
        val b = VitalAlert(AlertKind.REST_TACHY_SEVERE, t0, 155)
        assertEquals(listOf(b), VitalAlerts.simulatedDue(listOf(a, b), setOf(AlertKind.REST_TACHY)))
    }

    // ---- exercise sessions (Health Connect) ----

    @Test fun exerciseSession_countsAsActive() {
        val session = ExerciseWindow(t0, t0.plusMinutes(30))
        val s = hr(12, 150)   // cycling: high heart rate, no steps at all
        assertTrue(VitalAlerts.heartAlerts(s, emptyList(), 28, nowAfter(s), listOf(session)).isEmpty())
        assertEquals(ActivityState.ACTIVE, VitalAlerts.activityState(emptyList(), t0.plusMinutes(5), listOf(session)))
    }

    @Test fun afterSession_recovering() {
        val session = listOf(ExerciseWindow(t0, t0.plusMinutes(30)))
        assertEquals(ActivityState.RECOVERING, VitalAlerts.activityState(emptyList(), t0.plusMinutes(38), session))
        assertEquals(ActivityState.RESTING, VitalAlerts.activityState(emptyList(), t0.plusMinutes(45), session))
    }

    @Test fun sessionAloneIsMovementData() {
        val session = ExerciseWindow(t0.minusMinutes(70), t0.minusMinutes(40))
        val s = hr(12, 125)   // sitting 40 min after the workout, no step data
        assertEquals(listOf(AlertKind.REST_TACHY), kinds(VitalAlerts.heartAlerts(s, emptyList(), 28, nowAfter(s), listOf(session))))
    }
}

