package com.healthapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Runs scenarios minute by minute through VitalAlerts, the way the simulator does. */
class WatchScenarioTest {
    private val t0: LocalDateTime = LocalDateTime.of(2026, 11, 28, 9, 0)
    private val age = 28

    /** Returns the first minute each alert kind appeared. */
    private fun run(vararg parts: Pair<WatchScenario, Int>): Map<AlertKind, Int> {
        val hr = mutableListOf<HrSample>(); val steps = mutableListOf<StepSample>(); val bp = mutableListOf<BpReading>()
        val first = mutableMapOf<AlertKind, Int>()
        var bpm = 70.0
        var minute = 0
        for ((scenario, length) in parts) {
            for (m in 0 until length) {
                val now = t0.plusMinutes(minute.toLong())
                val target = WatchSimulation.target(scenario, m, age)
                bpm = WatchSimulation.step(bpm, target.bpm)
                hr += HrSample(now, bpm.roundToInt())
                steps += StepSample(now, target.stepsPerMin)
                target.bp?.let { (s, d) -> bp += BpReading(now, s, d) }
                (VitalAlerts.heartAlerts(hr, steps, age, now) + VitalAlerts.bpAlerts(bp, now)).forEach { first.putIfAbsent(it.kind, minute) }
                minute++
            }
        }
        return first
    }

    @Test fun run_20min_neverAlerts() {
        assertEquals(emptyMap<AlertKind, Int>(), run(WatchScenario.RUN to 20))
    }

    @Test fun runThenRest_recovery_neverAlerts() {
        assertEquals(emptyMap<AlertKind, Int>(), run(WatchScenario.RUN to 15, WatchScenario.REST to 10))
    }

    @Test fun restTachy_warnsBy14min_thenUrgent() {
        val first = run(WatchScenario.REST_TACHY to 22)
        assertTrue("warning by minute 14: $first", (first[AlertKind.REST_TACHY] ?: 99) <= 14)
        assertTrue("urgent by minute 21: $first", (first[AlertKind.REST_TACHY_SEVERE] ?: 99) <= 21)
    }

    @Test fun overExertion_warnsBy10min() {
        val first = run(WatchScenario.OVER_EXERTION to 15)
        assertTrue("over-exertion by minute 10: $first", (first[AlertKind.OVER_EXERTION] ?: 99) <= 10)
        assertFalse(AlertKind.ABOVE_MAX_HR in first)
    }

    @Test fun bpCrisis_alertsAtMinute2() {
        assertEquals(2, run(WatchScenario.BP_CRISIS to 5)[AlertKind.BP_CRISIS])
    }
}
