package com.healthapp.domain.usecase

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** One heart-rate reading. */
data class HrSample(val time: LocalDateTime, val bpm: Int)

/** Steps counted in the minute ending at [time]. */
data class StepSample(val time: LocalDateTime, val steps: Int)

enum class ActivityState { RESTING, ACTIVE, RECOVERING }

enum class AlertLevel { TREND, WARNING, URGENT }

enum class AlertKind(val level: AlertLevel) {
    REST_TACHY(AlertLevel.WARNING),
    REST_TACHY_SEVERE(AlertLevel.URGENT),
    REST_BRADY(AlertLevel.WARNING),
    OVER_EXERTION(AlertLevel.WARNING),
    ABOVE_MAX_HR(AlertLevel.URGENT),
    BP_CRISIS(AlertLevel.URGENT),
    BP_HIGH(AlertLevel.WARNING),
    BP_SUSTAINED(AlertLevel.TREND)
}

/** [value]/[secondary]: bpm (secondary = max heart rate for exertion alerts), or systolic/diastolic (BP); [state]: activity when the heart rate was measured. */
data class VitalAlert(
    val kind: AlertKind,
    val at: LocalDateTime,
    val value: Int,
    val secondary: Int? = null,
    val state: ActivityState? = null,
    val simulated: Boolean = false
) {
    val level: AlertLevel get() = kind.level
}

/**
 * Early-warning rules for heart rate and blood pressure. The key idea: a high heart rate while
 * moving is expected, the same rate at rest is not. Thresholds: AHA hypertensive crisis (≥ 180/120),
 * home-BP hypertension (mean ≥ 135/85), resting tachycardia/bradycardia, and exertion above
 * 90 % / 100 % of the age-predicted maximum (220 − age). Warnings, not a diagnosis.
 */
object VitalAlerts {
    const val ACTIVE_STEPS = 500
    const val DEFAULT_AGE = 40
    const val MAX_GAP_MIN = 2L
    const val COOLDOWN_MIN = 30L
    const val RECENT_HOURS = 2L

    fun maxHeartRate(age: Int?): Int = 220 - (age ?: DEFAULT_AGE)

    /** ACTIVE: ≥ 500 steps in the 10 minutes before [at]. RECOVERING: resting now, but active in the 10 minutes before that. */
    fun activityState(steps: List<StepSample>, at: LocalDateTime): ActivityState {
        fun stepsIn(from: LocalDateTime, to: LocalDateTime) = steps.filter { it.time.isAfter(from) && !it.time.isAfter(to) }.sumOf { it.steps }
        return when {
            stepsIn(at.minusMinutes(10), at) >= ACTIVE_STEPS -> ActivityState.ACTIVE
            stepsIn(at.minusMinutes(20), at.minusMinutes(10)) >= ACTIVE_STEPS -> ActivityState.RECOVERING
            else -> ActivityState.RESTING
        }
    }

    fun heartAlerts(hr: List<HrSample>, steps: List<StepSample>, age: Int?, now: LocalDateTime): List<VitalAlert> {
        val recent = hr.filter { !it.time.isBefore(now.minusHours(RECENT_HOURS)) && !it.time.isAfter(now) }.sortedBy { it.time }
        if (recent.isEmpty()) return emptyList()
        val max = maxHeartRate(age)
        val state = recent.associate { it.time to activityState(steps, it.time) }
        fun resting(s: HrSample) = state[s.time] == ActivityState.RESTING
        fun active(s: HrSample) = state[s.time] == ActivityState.ACTIVE
        fun alert(kind: AlertKind, s: HrSample, secondary: Int? = null) = VitalAlert(kind, s.time, s.bpm, secondary, state[s.time])

        val out = mutableListOf<VitalAlert>()
        when {
            sustained(recent, 5) { resting(it) && it.bpm >= 150 } != null -> out += alert(AlertKind.REST_TACHY_SEVERE, recent.last())
            sustained(recent, 10) { resting(it) && it.bpm >= 120 } != null -> out += alert(AlertKind.REST_TACHY, recent.last())
            sustained(recent, 10) { resting(it) && it.bpm < 40 } != null -> out += alert(AlertKind.REST_BRADY, recent.last())
        }
        when {
            sustained(recent, 2) { active(it) && it.bpm > max } != null -> out += alert(AlertKind.ABOVE_MAX_HR, recent.last(), max)
            sustained(recent, 5) { active(it) && it.bpm > max * 0.9 } != null -> out += alert(AlertKind.OVER_EXERTION, recent.last(), max)
        }
        return out
    }

    fun bpAlerts(readings: List<BpReading>, now: LocalDateTime): List<VitalAlert> {
        val out = mutableListOf<VitalAlert>()
        val latest = readings.filter { !it.at.isAfter(now) && !it.at.isBefore(now.minusHours(RECENT_HOURS)) }.maxByOrNull { it.at }
        if (latest != null) {
            when {
                latest.systolic >= 180 || latest.diastolic >= 120 -> out += VitalAlert(AlertKind.BP_CRISIS, latest.at, latest.systolic, latest.diastolic)
                latest.systolic >= 160 || latest.diastolic >= 100 -> out += VitalAlert(AlertKind.BP_HIGH, latest.at, latest.systolic, latest.diastolic)
            }
        }
        val week = readings.filter { !it.at.isAfter(now) && !it.at.isBefore(now.minusDays(7)) }
        if (week.size >= 5) {
            val sys = week.map { it.systolic }.average()
            val dia = week.map { it.diastolic }.average()
            if (sys >= 135 || dia >= 85) out += VitalAlert(AlertKind.BP_SUSTAINED, week.maxOf { it.at }, sys.roundToInt(), dia.roundToInt())
        }
        return out
    }

    /** Drops alerts sent too recently: the same kind within 30 minutes, a trend more than once a day. */
    fun dueAlerts(candidates: List<VitalAlert>, lastSent: Map<AlertKind, LocalDateTime>, now: LocalDateTime): List<VitalAlert> =
        candidates.filter { a ->
            val last = lastSent[a.kind] ?: return@filter true
            if (a.level == AlertLevel.TREND) last.toLocalDate().isBefore(now.toLocalDate())
            else !last.isAfter(now.minusMinutes(COOLDOWN_MIN))
        }

    /** The run of samples ending at the latest one that all meet [ok], ≤ 2 min apart, spanning ≥ [minutes]. */
    private fun sustained(s: List<HrSample>, minutes: Long, ok: (HrSample) -> Boolean): HrSample? {
        if (s.isEmpty() || !ok(s.last())) return null
        var first = s.last()
        for (i in s.size - 2 downTo 0) {
            if (!ok(s[i]) || Duration.between(s[i].time, first.time).toMinutes() > MAX_GAP_MIN) break
            first = s[i]
        }
        return if (Duration.between(first.time, s.last().time).toMinutes() >= minutes) s.last() else null
    }
}
