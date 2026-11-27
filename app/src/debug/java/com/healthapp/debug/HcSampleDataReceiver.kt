package com.healthapp.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.Volume
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Pressure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * DEBUG BUILD ONLY. Writes clearly fake sample data (14 days, all imported types) into Health Connect for manual testing of the
 * import, or deletes everything this app wrote ("--es mode clear").
 */
class HcSampleDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hc = HealthConnectClient.getOrCreate(context)
                val all = TimeRangeFilter.between(Instant.EPOCH, Instant.now().plus(1, ChronoUnit.DAYS))
                if (intent.getStringExtra("mode") == "inspect") {
                    val range = TimeRangeFilter.between(Instant.now().minus(3, ChronoUnit.DAYS), Instant.now())
                    val raw = hc.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(StepsRecord::class, range)).records
                    val agg = hc.aggregate(androidx.health.connect.client.request.AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))
                    Log.i(TAG, "raw steps records=${raw.size} sum=${raw.sumOf { it.count }} origins=${raw.map { it.metadata.dataOrigin.packageName }.toSet()} aggregate=${agg[StepsRecord.COUNT_TOTAL]} aggOrigins=${agg.dataOrigins.map { it.packageName }}")
                } else if (intent.getStringExtra("mode") == "clear") {
                    TYPES.forEach { hc.deleteRecords(it, all) } // only deletes records written by this app
                    Log.i(TAG, "cleared sample data")
                } else {
                    // Replace earlier samples instead of stacking duplicates.
                    TYPES.forEach { hc.deleteRecords(it, all) }
                    val records = sample()
                    hc.insertRecords(records)
                    Log.i(TAG, "inserted ${records.size} sample records (14 days)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 14 days of plausible data for every type the app imports (fixed seed, so every run looks the same).
     * Nothing is written after "now".
     */
    private fun sample(): List<androidx.health.connect.client.records.Record> {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now()
        val rnd = Random(2026)
        fun at(day: LocalDate, h: Int, m: Int = 0): Instant = day.atTime(h, m).atZone(zone).toInstant()
        fun off(i: Instant) = zone.rules.getOffset(i)
        val out = mutableListOf<androidx.health.connect.client.records.Record>()

        for (back in 13 downTo 0) {
            val day = today.minusDays(back.toLong())
            // Steps in three walks, plus active energy for the same walks.
            listOf(Triple(7, 45, 0.30), Triple(12, 30, 0.25), Triple(18, 60, 0.45)).forEach { (hour, minutes, share) ->
                val start = at(day, hour)
                val end = start.plusSeconds(minutes * 60L)
                if (end.isAfter(now)) return@forEach
                val steps = ((5000 + rnd.nextInt(6500)) * share).toLong()
                out += StepsRecord(start, off(start), end, off(end), steps, Metadata())
                out += ActiveCaloriesBurnedRecord(start, off(start), end, off(end), Energy.kilocalories(steps * 0.04), Metadata())
            }
            // Sleep: the night that ends on this day.
            val bed = at(day.minusDays(1), 22, 30).plusSeconds(rnd.nextLong(0, 80) * 60)
            val wake = at(day, 6, 0).plusSeconds(rnd.nextLong(0, 75) * 60)
            if (wake.isBefore(now)) out += SleepSessionRecord(bed, off(bed), wake, off(wake), title = "Sample sleep", metadata = Metadata())
            // Heart rate: a sample every 30 minutes from 08:00 to 21:00.
            val hrStart = at(day, 8)
            val samples = (0 until 27).map { hrStart.plusSeconds(it * 1800L) }.filter { it.isBefore(now) }
                .map { HeartRateRecord.Sample(it, (62 + rnd.nextInt(28)).toLong()) }
            if (samples.size >= 2) out += HeartRateRecord(samples.first().time, off(hrStart), samples.last().time.plusSeconds(1), off(hrStart), samples, Metadata())
            // Morning measurements.
            val morning = at(day, 6, 45)
            if (morning.isBefore(now)) {
                if (back % 2 == 0) out += WeightRecord(morning, off(morning), Mass.kilograms(68.9 - (13 - back) * 0.05 + rnd.nextDouble(-0.2, 0.2)), Metadata())
                if (back % 2 == 1 || back == 0) out += BloodPressureRecord(morning, off(morning),
                    Pressure.millimetersOfMercury(115.0 + rnd.nextInt(14)), Pressure.millimetersOfMercury(72.0 + rnd.nextInt(10)), metadata = Metadata())
                if (back % 3 == 0) out += BloodGlucoseRecord(morning, off(morning), BloodGlucose.millimolesPerLiter(4.8 + rnd.nextInt(10) / 10.0),
                    BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD, MealType.MEAL_TYPE_BREAKFAST, BloodGlucoseRecord.RELATION_TO_MEAL_FASTING, Metadata())
                // What a smartwatch measures overnight / on waking.
                out += OxygenSaturationRecord(time = morning, zoneOffset = off(morning), percentage = Percentage(95.0 + rnd.nextInt(5)), metadata = Metadata())
                out += RestingHeartRateRecord(time = morning, zoneOffset = off(morning), beatsPerMinute = (56 + rnd.nextInt(9)).toLong(), metadata = Metadata())
                out += BodyTemperatureRecord(time = morning, zoneOffset = off(morning), temperature = Temperature.celsius(36.4 + rnd.nextInt(7) / 10.0), metadata = Metadata())
                out += RespiratoryRateRecord(time = morning, zoneOffset = off(morning), rate = (13 + rnd.nextInt(5)).toDouble(), metadata = Metadata())
            }
            // Workouts: a 40-minute run every other evening, a 45-minute bike ride every third morning.
            fun workout(startH: Int, startM: Int, minutes: Long, type: Int, title: String) {
                val start = at(day, startH, startM)
                val end = start.plusSeconds(minutes * 60)
                if (end.isBefore(now)) out += ExerciseSessionRecord(startTime = start, startZoneOffset = off(start), endTime = end,
                    endZoneOffset = off(end), exerciseType = type, title = title, metadata = Metadata())
            }
            if (back % 2 == 0) workout(18, 0, 40, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, "Sample run")
            if (back % 3 == 1) workout(6, 0, 45, ExerciseSessionRecord.EXERCISE_TYPE_BIKING, "Sample ride")
            // Drinks logged in another app: four glasses of 250 ml.
            listOf(9, 12, 15, 18).forEach { h ->
                val start = at(day, h)
                if (start.plusSeconds(60).isBefore(now)) out += HydrationRecord(startTime = start, startZoneOffset = off(start),
                    endTime = start.plusSeconds(60), endZoneOffset = off(start), volume = Volume.liters(0.25), metadata = Metadata())
            }
        }
        return out
    }

    companion object {
        private const val TAG = "HcSampleData"
        private val TYPES = listOf(StepsRecord::class, ActiveCaloriesBurnedRecord::class, SleepSessionRecord::class, HeartRateRecord::class,
            WeightRecord::class, BloodPressureRecord::class, BloodGlucoseRecord::class, ExerciseSessionRecord::class,
            OxygenSaturationRecord::class, RestingHeartRateRecord::class, BodyTemperatureRecord::class, RespiratoryRateRecord::class,
            HydrationRecord::class)
    }
}
