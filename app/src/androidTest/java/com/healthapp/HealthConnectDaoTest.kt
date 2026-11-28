package com.healthapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.healthapp.data.local.HealthDatabase
import com.healthapp.data.local.entity.ActivityLogEntity
import com.healthapp.data.local.entity.VitalLogEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** The Health Connect sync deletes rows with SQL: only imported rows in the re-read span may go. */
@RunWith(AndroidJUnit4::class)
class HealthConnectDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HealthDatabase::class.java).build()
    private val from: LocalDateTime = LocalDateTime.of(2026, 11, 27, 10, 0)

    @After fun close() = db.close()

    private fun hr(id: String, at: LocalDateTime) = VitalLogEntity(id, "acc", "HEART_RATE", 70.0, null, "bpm", "HEALTH_CONNECT", at)
    private fun day(id: String, date: String) = ActivityLogEntity(id, "acc", "WALKING", 0, 5000, 0.0, 0, "LOW", LocalDateTime.parse("${date}T23:59"), date)

    @Test fun applySync_replacesOnlyImportedTotalsInTheSpan() = runBlocking {
        val vitals = listOf(
            hr("manual-1", from.plusHours(1)),                  // typed by the user
            hr("demo_hr_1", from.plusHours(1)),                 // sample data
            hr("hc_hr_2026-11-27T09:00", from.minusHours(1)),   // imported, before the span
            hr("hc_hr_2026-11-27T10:00", from),                 // imported, exactly at the start (inclusive)
            hr("hc_hr_2026-11-27T11:00", from.plusHours(1)),
            VitalLogEntity("hc_bp_x1", "acc", "BLOOD_PRESSURE", 180.0, 120.0, "mmHg", "HEALTH_CONNECT", from.minusDays(3)),
            hr("hc_hr_2026-11-27T12:00", from.plusHours(2)).copy(accountId = "other")   // another account
        )
        val days = listOf(day("hc_day_2026-11-26", "2026-11-26"), day("hc_day_2026-11-27", "2026-11-27"), day("manual-day", "2026-11-27"))
        db.healthConnectDao().upsertAll(days, emptyList(), emptyList(), vitals)

        db.healthConnectDao().applySync(
            "acc", deleteIds = listOf("hc_bp_x1"), stepsFromDate = "2026-11-27", heartFrom = from,
            activities = emptyList(), sleep = emptyList(), weights = emptyList(), vitals = emptyList(), water = emptyList()
        )

        val leftVitals = db.dataManagementDao().vitalLogs("acc").map { it.id }.toSet()
        assertEquals(setOf("manual-1", "demo_hr_1", "hc_hr_2026-11-27T09:00"), leftVitals)
        assertEquals(1, db.dataManagementDao().vitalLogs("other").size)
        assertEquals(setOf("hc_day_2026-11-26", "manual-day"), db.dataManagementDao().activityLogs("acc").map { it.id }.toSet())
    }

    @Test fun importedIdsSince_onlyThisPrefixAccountAndSpan() = runBlocking {
        val bp = { id: String, at: LocalDateTime, acc: String -> VitalLogEntity(id, acc, "BLOOD_PRESSURE", 120.0, 80.0, "mmHg", "HEALTH_CONNECT", at) }
        db.healthConnectDao().upsertAll(emptyList(), emptyList(), emptyList(), listOf(
            bp("hc_bp_a", from, "acc"),                      // inclusive start
            bp("hc_bp_b", from.minusMinutes(1), "acc"),      // before the span
            bp("hc_bp_c", from, "other"),                    // another account
            bp("hc_glu_d", from, "acc"),                     // another type
            bp("manual-e", from, "acc")                      // typed by the user
        ))
        assertEquals(listOf("hc_bp_a"), db.healthConnectDao().importedVitalIdsSince("acc", "hc\\_bp\\_%", from))
    }
}
