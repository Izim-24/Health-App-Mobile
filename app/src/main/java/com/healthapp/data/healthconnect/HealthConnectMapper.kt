package com.healthapp.data.healthconnect

import com.healthapp.data.local.entity.ActivityLogEntity
import com.healthapp.data.local.entity.SleepLogEntity
import com.healthapp.data.local.entity.VitalLogEntity
import com.healthapp.data.local.entity.WeightLogEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/*
 * Plain snapshots of Health Connect data (already converted to local time) so the mapping to
 * Room rows can be unit-tested without the Health Connect SDK.
 */
data class HcDaySteps(val date: LocalDate, val steps: Long, val activeKcal: Double?)
data class HcRawSteps(val origin: String, val start: LocalDateTime, val count: Long)
data class HcSleep(val uid: String, val start: LocalDateTime, val end: LocalDateTime)
data class HcHeartSample(val time: LocalDateTime, val bpm: Long)
data class HcWeight(val uid: String, val time: LocalDateTime, val kg: Double)
data class HcBloodPressure(val uid: String, val time: LocalDateTime, val systolic: Double, val diastolic: Double)
data class HcGlucose(val uid: String, val time: LocalDateTime, val mmolPerL: Double)

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
        source = SOURCE,
        recordedAt = bp.time
    )

    fun glucose(accountId: String, g: HcGlucose) = VitalLogEntity(
        id = "${ID_PREFIX}glu_${g.uid}",
        accountId = accountId,
        type = "GLUCOSE",
        value = (g.mmolPerL * 10).roundToInt() / 10.0,
        secondaryValue = null,
        unit = "mmol/L",
        source = SOURCE,
        recordedAt = g.time
    )

    /** First sync looks back [initialDays]; later syncs re-read a 2-day overlap to catch late-arriving data. */
    fun syncStart(lastSync: LocalDateTime?, now: LocalDateTime, initialDays: Long = 30): LocalDateTime {
        val earliest = now.toLocalDate().minusDays(initialDays - 1).atStartOfDay()
        val fromLast = lastSync?.toLocalDate()?.minusDays(2)?.atStartOfDay() ?: return earliest
        return maxOf(fromLast, earliest)
    }
}
