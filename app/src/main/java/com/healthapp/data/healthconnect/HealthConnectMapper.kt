package com.healthapp.data.healthconnect

import com.healthapp.data.local.entity.ActivityLogEntity
import com.healthapp.data.local.entity.SleepLogEntity
import com.healthapp.data.local.entity.VitalLogEntity
import com.healthapp.data.local.entity.WeightLogEntity
import com.healthapp.data.local.entity.WaterLogEntity
import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/*
 * Plain snapshots of Health Connect data (already converted to local time) so the mapping to
 * Room rows can be unit-tested without the Health Connect SDK.
 */
data class HcDaySteps(val date: LocalDate, val steps: Long, val activeKcal: Double?)
data class HcRawSteps(val origin: String, val start: LocalDateTime, val count: Long, val end: LocalDateTime = start)
data class HcSleep(val uid: String, val start: LocalDateTime, val end: LocalDateTime)
data class HcHeartSample(val time: LocalDateTime, val bpm: Long)
data class HcWeight(val uid: String, val time: LocalDateTime, val kg: Double)
data class HcBloodPressure(val uid: String, val time: LocalDateTime, val systolic: Double, val diastolic: Double, val origin: String = "")
data class HcGlucose(val uid: String, val time: LocalDateTime, val mmolPerL: Double, val origin: String = "")
data class HcSpo2(val uid: String, val time: LocalDateTime, val percent: Double, val origin: String)
data class HcRestingHr(val uid: String, val time: LocalDateTime, val bpm: Long, val origin: String)
data class HcTemperature(val uid: String, val time: LocalDateTime, val celsius: Double, val origin: String)
data class HcRespiratory(val uid: String, val time: LocalDateTime, val perMinute: Double, val origin: String)
data class HcHydration(val uid: String, val start: LocalDateTime, val liters: Double)
data class HcExercise(val uid: String, val start: LocalDateTime, val end: LocalDateTime, val type: Int, val title: String?)

/**
 * Maps Health Connect snapshots to Room rows. Every row gets a deterministic id starting with
 * [ID_PREFIX], so re-syncing the same window overwrites instead of duplicating, and imported
 * rows can be removed on disconnect without touching manual entries.
 */
object HealthConnectMapper {
    const val ID_PREFIX = "hc_"
    const val SOURCE = "HEALTH_CONNECT"
    const val SLEEP_NOTE = "Health Connect"

    fun isImported(id: String) = id.startsWith(ID_PREFIX)

    /** "HEALTH_CONNECT:<package of the app that wrote it>", or plain "HEALTH_CONNECT" when unknown. */
    fun source(origin: String): String = if (origin.isBlank()) SOURCE else "$SOURCE:$origin"

    private fun vital(id: String, accountId: String, type: String, value: Double, unit: String, origin: String, time: LocalDateTime) =
        VitalLogEntity(id = id, accountId = accountId, type = type, value = value, secondaryValue = null, unit = unit,
            source = source(origin), recordedAt = time)

    /** Blood oxygen, 70–100 %. */
    fun spo2(accountId: String, r: HcSpo2): VitalLogEntity? =
        if (r.percent !in 70.0..100.0) null
        else vital("${ID_PREFIX}spo2_${r.uid}", accountId, "OXYGEN_SATURATION", r.percent.roundToInt().toDouble(), "%", r.origin, r.time)

    /** Resting heart rate, 25–200 bpm. */
    fun restingHeartRate(accountId: String, r: HcRestingHr): VitalLogEntity? =
        if (r.bpm !in 25..200) null
        else vital("${ID_PREFIX}rhr_${r.uid}", accountId, "RESTING_HEART_RATE", r.bpm.toDouble(), "bpm", r.origin, r.time)

    /** Body temperature, 34–42 °C, one decimal. */
    fun temperature(accountId: String, r: HcTemperature): VitalLogEntity? =
        if (r.celsius !in 34.0..42.0) null
        else vital("${ID_PREFIX}temp_${r.uid}", accountId, "BODY_TEMPERATURE", (r.celsius * 10).roundToInt() / 10.0, "°C", r.origin, r.time)

    /** Breaths per minute, 4–60. */
    fun respiratoryRate(accountId: String, r: HcRespiratory): VitalLogEntity? =
        if (r.perMinute !in 4.0..60.0) null
        else vital("${ID_PREFIX}resp_${r.uid}", accountId, "RESPIRATORY_RATE", r.perMinute.roundToInt().toDouble(), "/min", r.origin, r.time)

    /** A drink logged in another app, 1–5000 ml; counts towards the water goal on its start day. */
    fun hydration(accountId: String, r: HcHydration): WaterLogEntity? {
        val ml = (r.liters * 1000).roundToInt()
        if (ml !in 1..5000) return null
        return WaterLogEntity(id = "${ID_PREFIX}water_${r.uid}", accountId = accountId, amountMl = ml, recordedAt = r.start, date = r.start.toLocalDate().toString())
    }

    /** A workout (1 min–24 h) as an activity row without steps, so daily step totals are never counted twice. */
    fun exercise(accountId: String, r: HcExercise): ActivityLogEntity? {
        val minutes = Duration.between(r.start, r.end).toMinutes()
        if (minutes !in 1..(24 * 60)) return null
        return ActivityLogEntity(
            id = "${ID_PREFIX}ex_${r.uid}", accountId = accountId, activityType = exerciseType(r.type),
            durationMinutes = minutes.toInt(), steps = 0, distance = 0.0, caloriesBurned = 0, intensity = "MODERATE",
            recordedAt = r.end, date = r.start.toLocalDate().toString()
        )
    }

    fun exerciseWindow(r: HcExercise) = com.healthapp.domain.usecase.ExerciseWindow(r.start, r.end)

    private fun exerciseType(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "RUNNING"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "CYCLING"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "WALKING"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "SWIMMING"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "YOGA"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "GYM"
        else -> "OTHER"
    }

    fun steps(accountId: String, day: HcDaySteps, syncedAt: LocalDateTime): ActivityLogEntity? {
        if (day.steps <= 0 && (day.activeKcal ?: 0.0) <= 0.0) return null
        return ActivityLogEntity(
            id = "${ID_PREFIX}day_${day.date}",
            accountId = accountId,
            activityType = "WALKING",
            durationMinutes = 0,
            steps = day.steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            distance = 0.0,
            caloriesBurned = (day.activeKcal ?: 0.0).roundToInt(),
            intensity = "LOW",
            // Past days are stamped at their end so "latest" ordering stays chronological.
            recordedAt = if (day.date == syncedAt.toLocalDate()) syncedAt else day.date.atTime(23, 59),
            date = day.date.toString()
        )
    }

    /**
     * Fallback daily step totals from raw records, used only for days where Health Connect's
     * aggregate is empty (e.g. no app in the user's data-source priority list). To avoid
     * double-counting phone + watch, each day takes the single source with the highest total.
     */
    fun dailyStepsFromRaw(records: List<HcRawSteps>): Map<LocalDate, Long> =
        records.groupBy { it.start.toLocalDate() }
            .mapValues { (_, day) -> day.groupBy { it.origin }.values.maxOf { r -> r.sumOf { it.count } } }

    /** Uses the aggregate where it has steps, else the raw fallback; keeps aggregate calories. */
    fun mergeDailySteps(aggregate: List<HcDaySteps>, fallback: Map<LocalDate, Long>): List<HcDaySteps> {
        val byDate = aggregate.associateBy { it.date }.toMutableMap()
        fallback.forEach { (date, steps) ->
            val agg = byDate[date]
            if (agg == null || agg.steps <= 0) byDate[date] = HcDaySteps(date, steps, agg?.activeKcal)
        }
        return byDate.values.sortedBy { it.date }
    }

    /** Sleep is dated by the wake-up day, matching manual entry. Sessions over 24h are ignored. */
    fun sleep(accountId: String, s: HcSleep): SleepLogEntity? {
        val minutes = Duration.between(s.start, s.end).toMinutes()
        if (minutes !in 1..(24 * 60)) return null
        return SleepLogEntity(
            id = "${ID_PREFIX}sleep_${s.uid}",
            accountId = accountId,
            bedtime = s.start,
            wakeupTime = s.end,
            durationMinutes = minutes.toInt(),
            sleepQuality = null,
            notes = SLEEP_NOTE,
            date = s.end.toLocalDate().toString()
        )
    }

    /**
     * Heart rate for the alert rules: the last [com.healthapp.domain.usecase.VitalAlerts.RECENT_HOURS] hours, one sample
     * per minute (the highest of that minute, so a short spike is not averaged away).
     */
    fun alertHeart(samples: List<HcHeartSample>, now: LocalDateTime): List<com.healthapp.domain.usecase.HrSample> =
        samples.filter { it.bpm in 20..300 && !it.time.isAfter(now) && !it.time.isBefore(now.minusHours(com.healthapp.domain.usecase.VitalAlerts.RECENT_HOURS)) }
            .groupBy { it.time.withSecond(0).withNano(0) }
            .map { (minute, inMinute) -> com.healthapp.domain.usecase.HrSample(minute, inMinute.maxOf { it.bpm }.toInt()) }
            .sortedBy { it.time }

    /**
     * Steps per minute for the alert rules (last 2 hours). Each record's count is spread evenly over its minutes;
     * when phone and watch both saw the same walk, the larger source per minute is kept instead of adding them.
     * A sample at time t holds the steps of the minute ending at t.
     */
    fun alertSteps(raw: List<HcRawSteps>, now: LocalDateTime): List<com.healthapp.domain.usecase.StepSample> {
        val from = now.minusHours(com.healthapp.domain.usecase.VitalAlerts.RECENT_HOURS)
        val perOrigin = mutableMapOf<Pair<String, LocalDateTime>, Double>()
        for (r in raw) {
            val start = r.start.withSecond(0).withNano(0)
            val minutes = java.time.Duration.between(start, r.end).toMinutes().coerceAtLeast(1)
            val each = r.count.toDouble() / minutes
            for (i in 1..minutes) {
                val t = start.plusMinutes(i)
                if (t.isAfter(from) && !t.isAfter(now)) perOrigin.merge(r.origin to t, each, Double::plus)
            }
        }
        return perOrigin.entries.groupBy { it.key.second }
            .map { (t, byOrigin) -> com.healthapp.domain.usecase.StepSample(t, byOrigin.maxOf { it.value }.roundToInt()) }
            .sortedBy { it.time }
    }

    /** Wearables write a sample every few seconds; keep one averaged reading per clock hour. */
    fun heartRateHourly(accountId: String, samples: List<HcHeartSample>): List<VitalLogEntity> =
        samples.filter { it.bpm in 20..300 }
            .groupBy { it.time.withMinute(0).withSecond(0).withNano(0) }
            .map { (hour, inHour) ->
                VitalLogEntity(
                    id = "${ID_PREFIX}hr_$hour",
                    accountId = accountId,
                    type = "HEART_RATE",
                    value = inHour.map { it.bpm }.average().roundToInt().toDouble(),
                    secondaryValue = null,
                    unit = "bpm",
                    source = SOURCE,
                    recordedAt = inHour.maxOf { it.time }
                )
            }
            .sortedBy { it.recordedAt }

    fun weight(accountId: String, w: HcWeight): WeightLogEntity? {
        if (w.kg !in 2.0..500.0) return null
        return WeightLogEntity(
            id = "${ID_PREFIX}w_${w.uid}",
            accountId = accountId,
            weight = (w.kg * 10).roundToInt() / 10.0,
            recordedAt = w.time,
            date = w.time.toLocalDate().toString()
        )
    }

    fun bloodPressure(accountId: String, bp: HcBloodPressure) = VitalLogEntity(
        id = "${ID_PREFIX}bp_${bp.uid}",
        accountId = accountId,
        type = "BLOOD_PRESSURE",
        value = bp.systolic.roundToInt().toDouble(),
        secondaryValue = bp.diastolic.roundToInt().toDouble(),
        unit = "mmHg",
        source = source(bp.origin),
        recordedAt = bp.time
    )

    fun glucose(accountId: String, g: HcGlucose) = VitalLogEntity(
        id = "${ID_PREFIX}glu_${g.uid}",
        accountId = accountId,
        type = "GLUCOSE",
        value = (g.mmolPerL * 10).roundToInt() / 10.0,
        secondaryValue = null,
        unit = "mmol/L",
        source = source(g.origin),
        recordedAt = g.time
    )

    /** First sync looks back [initialDays]; later syncs re-read a 2-day overlap to catch late-arriving data. */
    fun syncStart(lastSync: LocalDateTime?, now: LocalDateTime, initialDays: Long = 30): LocalDateTime {
        val earliest = now.toLocalDate().minusDays(initialDays - 1).atStartOfDay()
        val fromLast = lastSync?.toLocalDate()?.minusDays(2)?.atStartOfDay() ?: return earliest
        return maxOf(fromLast, earliest)
    }
}
