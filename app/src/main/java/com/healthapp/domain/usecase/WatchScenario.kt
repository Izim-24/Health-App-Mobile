package com.healthapp.domain.usecase

import kotlin.math.roundToInt

/** Situations the demo watch can play. */
enum class WatchScenario { REST, RUN, OVER_EXERTION, REST_TACHY, BP_CRISIS }

/** What the simulated body is heading towards in a given minute. [bp] = a blood-pressure reading taken that minute. */
data class WatchTarget(val bpm: Int, val stepsPerMin: Int, val bp: Pair<Int, Int>? = null)

/** Simulated watch data for demos (never stored). Heart rate moves gradually so switching scenarios looks real. */
object WatchSimulation {
    /** [minute] = minutes since this scenario started. */
    fun target(s: WatchScenario, minute: Int, age: Int?): WatchTarget = when (s) {
        WatchScenario.REST -> WatchTarget(70, 0)
        WatchScenario.RUN -> WatchTarget(155, 150)
        WatchScenario.OVER_EXERTION -> WatchTarget((VitalAlerts.maxHeartRate(age) * 0.95).roundToInt(), 160)
        WatchScenario.REST_TACHY -> WatchTarget(if (minute < 12) 128 else 158, 0)
        WatchScenario.BP_CRISIS -> WatchTarget(72, 0, if (minute == 2) 186 to 118 else null)
    }

    /** Moves the heart rate half-way to the target each minute (recovery after exercise decays the same way). */
    fun step(currentBpm: Double, target: Int): Double = currentBpm + (target - currentBpm) * 0.5
}
