package com.healthapp.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Pressure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * DEBUG BUILD ONLY. Writes clearly fake sample data into Health Connect for manual testing of the
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
                    listOf(StepsRecord::class, SleepSessionRecord::class, HeartRateRecord::class, WeightRecord::class, BloodPressureRecord::class)
                        .forEach { hc.deleteRecords(it, all) } // only deletes records written by this app
                    Log.i(TAG, "cleared sample data")
                } else {
                    hc.insertRecords(sample())
                    Log.i(TAG, "inserted sample data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun sample(): List<androidx.health.connect.client.records.Record> {
        val zone = ZoneId.systemDefault()
        val offset = zone.rules.getOffset(Instant.now())
        val today = LocalDate.now()
        val now = Instant.now()
        val out = mutableListOf<androidx.health.connect.client.records.Record>()
        // Steps: yesterday and today (today ends "now").
        out += StepsRecord(today.minusDays(1).atTime(9, 0).atZone(zone).toInstant(), offset, today.minusDays(1).atTime(18, 0).atZone(zone).toInstant(), offset, 8123, Metadata())
        out += StepsRecord(today.atStartOfDay(zone).toInstant().plusSeconds(8 * 3600), offset, now.minusSeconds(60), offset, 3456, Metadata())
        // Sleep last night 23:10 -> 06:40.
        out += SleepSessionRecord(today.minusDays(1).atTime(23, 10).atZone(zone).toInstant(), offset, today.atTime(6, 40).atZone(zone).toInstant(), offset, title = "Sample sleep", metadata = Metadata())
        // Heart rate: 3 samples within the last hour.
        val hrStart = now.minusSeconds(40 * 60)
        out += HeartRateRecord(hrStart, offset, now.minusSeconds(60), offset,
            listOf(HeartRateRecord.Sample(hrStart, 64), HeartRateRecord.Sample(hrStart.plusSeconds(600), 70), HeartRateRecord.Sample(hrStart.plusSeconds(1200), 73)), Metadata())
        out += WeightRecord(now.minusSeconds(3600), offset, Mass.kilograms(68.4), Metadata())
        out += BloodPressureRecord(now.minusSeconds(1800), offset, Pressure.millimetersOfMercury(118.0), Pressure.millimetersOfMercury(76.0), metadata = Metadata())
        return out
    }

    companion object { private const val TAG = "HcSampleData" }
}
