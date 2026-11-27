package com.healthapp.data.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HealthConnectMapperTest {
    private val acc = "acc"
    private val now = LocalDateTime.of(2026, 11, 14, 16, 30)

    @Test fun steps_deterministicIdPerDay_andTimestamps() {
        val today = HealthConnectMapper.steps(acc, HcDaySteps(now.toLocalDate(), 4200, 180.4), now)!!
        assertEquals("hc_day_2026-11-14", today.id)
        assertEquals(4200, today.steps)
        assertEquals(180, today.caloriesBurned)
        assertEquals(now, today.recordedAt)
        val past = HealthConnectMapper.steps(acc, HcDaySteps(LocalDate.of(2026, 11, 10), 100, null), now)!!
        assertEquals(LocalDateTime.of(2026, 11, 10, 23, 59), past.recordedAt)
        assertNull(HealthConnectMapper.steps(acc, HcDaySteps(now.toLocalDate(), 0, null), now))
    }

    @Test fun rawStepsFallback_takesBestSingleSourcePerDay_andOnlyFillsEmptyDays() {
        val d1 = LocalDate.of(2026, 11, 13); val d2 = LocalDate.of(2026, 11, 14)
        val raw = listOf(
            HcRawSteps("phone", d1.atTime(9, 0), 3000), HcRawSteps("phone", d1.atTime(17, 0), 2000),
            HcRawSteps("watch", d1.atTime(9, 0), 5500), // same walk seen by both: must not add up
            HcRawSteps("phone", d2.atTime(8, 0), 1200)
        )
        val fallback = HealthConnectMapper.dailyStepsFromRaw(raw)
        assertEquals(mapOf(d1 to 5500L, d2 to 1200L), fallback)
        val merged = HealthConnectMapper.mergeDailySteps(listOf(HcDaySteps(d1, 7000, 250.0), HcDaySteps(d2, 0, 90.0)), fallback)
        assertEquals(listOf(HcDaySteps(d1, 7000, 250.0), HcDaySteps(d2, 1200, 90.0)), merged)
    }

    @Test fun sleep_datedByWakeDay_andRejectsBadDurations() {
        val s = HealthConnectMapper.sleep(acc, HcSleep("u1", LocalDateTime.of(2026, 11, 13, 23, 15), LocalDateTime.of(2026, 11, 14, 6, 45)))!!
        assertEquals("2026-11-14", s.date)
        assertEquals(450, s.durationMinutes)
        assertEquals("hc_sleep_u1", s.id)
        assertNull(HealthConnectMapper.sleep(acc, HcSleep("u2", now, now)))
        assertNull(HealthConnectMapper.sleep(acc, HcSleep("u3", now, now.plusHours(25))))
    }

    @Test fun heartRate_averagedPerHour_dropsImplausible() {
        val samples = listOf(
            HcHeartSample(LocalDateTime.of(2026, 11, 14, 9, 5), 60),
            HcHeartSample(LocalDateTime.of(2026, 11, 14, 9, 40), 71),
            HcHeartSample(LocalDateTime.of(2026, 11, 14, 10, 1), 90),
            HcHeartSample(LocalDateTime.of(2026, 11, 14, 10, 2), 999)
        )
        val out = HealthConnectMapper.heartRateHourly(acc, samples)
        assertEquals(2, out.size)
        assertEquals(66.0, out[0].value, 0.0) // 65.5 rounds half-up to 66
        assertEquals(LocalDateTime.of(2026, 11, 14, 9, 40), out[0].recordedAt)
        assertEquals("hc_hr_2026-11-14T09:00", out[0].id)
        assertEquals(90.0, out[1].value, 0.0)
        assertTrue(out.all { it.source == HealthConnectMapper.SOURCE })
    }

    @Test fun weight_bp_glucose_mapping() {
        assertEquals(72.4, HealthConnectMapper.weight(acc, HcWeight("w", now, 72.38))!!.weight, 0.0)
        assertNull(HealthConnectMapper.weight(acc, HcWeight("w", now, 0.5)))
        val bp = HealthConnectMapper.bloodPressure(acc, HcBloodPressure("b", now, 121.6, 79.2))
        assertEquals(122.0, bp.value, 0.0); assertEquals(79.0, bp.secondaryValue!!, 0.0)
        assertEquals("mmol/L", HealthConnectMapper.glucose(acc, HcGlucose("g", now, 5.55)).unit)
    }

    @Test fun syncStart_firstSyncUsesInitialWindow_laterOverlapsTwoDays() {
        assertEquals(LocalDateTime.of(2026, 10, 16, 0, 0), HealthConnectMapper.syncStart(null, now))
        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0), HealthConnectMapper.syncStart(now.minusHours(1), now))
        // A very old last sync is capped at the initial window.
        assertEquals(LocalDateTime.of(2026, 10, 16, 0, 0), HealthConnectMapper.syncStart(now.minusDays(90), now))
    }

    // ---------------- Inputs for health alerts ----------------

    @Test fun alertSteps_spreadsCountOverMinutes() {
        val t = LocalDateTime.of(2026, 11, 28, 9, 0)
        val out = HealthConnectMapper.alertSteps(listOf(HcRawSteps("watch", t, 600, t.plusMinutes(10))), t.plusMinutes(30))
        assertEquals(10, out.size)
        assertTrue(out.all { it.steps == 60 })
        assertEquals(t.plusMinutes(1), out.first().time)   // steps of the minute ending at that time
        assertEquals(t.plusMinutes(10), out.last().time)
    }

    @Test fun alertSteps_sameWalkFromTwoSources_notDoubled() {
        val t = LocalDateTime.of(2026, 11, 28, 9, 0)
        val raw = listOf(HcRawSteps("phone", t, 600, t.plusMinutes(10)), HcRawSteps("watch", t, 600, t.plusMinutes(10)))
        assertEquals(600, HealthConnectMapper.alertSteps(raw, t.plusMinutes(30)).sumOf { it.steps })
    }

    @Test fun alertSteps_onlyLast2h() {
        val t = LocalDateTime.of(2026, 11, 28, 6, 0)
        assertTrue(HealthConnectMapper.alertSteps(listOf(HcRawSteps("watch", t, 600, t.plusMinutes(10))), t.plusHours(4)).isEmpty())
    }

    @Test fun alertHeart_keepsLast2hMaxPerMinute() {
        val t = LocalDateTime.of(2026, 11, 28, 10, 0)
        val samples = listOf(
            HcHeartSample(t.plusSeconds(10), 80), HcHeartSample(t.plusSeconds(40), 95),
            HcHeartSample(t.plusSeconds(80), 90), HcHeartSample(t.minusHours(3), 150)
        )
        val out = HealthConnectMapper.alertHeart(samples, t.plusMinutes(5))
        assertEquals(listOf(com.healthapp.domain.usecase.HrSample(t, 95), com.healthapp.domain.usecase.HrSample(t.plusMinutes(1), 90)), out)
    }

    // ---------------- New Health Connect types ----------------

    private val at = LocalDateTime.of(2026, 11, 28, 7, 30)
    private val samsung = "com.sec.android.app.shealth"

    @Test fun spo2_percentRangeAndSource() {
        val row = HealthConnectMapper.spo2("acc", HcSpo2("u1", at, 97.0, samsung))!!
        assertEquals("hc_spo2_u1", row.id)
        assertEquals("OXYGEN_SATURATION", row.type)
        assertEquals(97.0, row.value, 0.0)
        assertEquals("%", row.unit)
        assertEquals("HEALTH_CONNECT:$samsung", row.source)
        assertNull(HealthConnectMapper.spo2("acc", HcSpo2("u2", at, 50.0, samsung)))
    }

    @Test fun temperature_oneDecimal() {
        val row = HealthConnectMapper.temperature("acc", HcTemperature("u1", at, 36.84, ""))!!
        assertEquals(36.8, row.value, 0.0)
        assertEquals("°C", row.unit)
        assertEquals("HEALTH_CONNECT", row.source)   // unknown origin
        assertNull(HealthConnectMapper.temperature("acc", HcTemperature("u2", at, 45.0, "")))
    }

    @Test fun restingHr_and_respiratory() {
        assertEquals("bpm", HealthConnectMapper.restingHeartRate("acc", HcRestingHr("u1", at, 58, samsung))!!.unit)
        assertEquals("RESTING_HEART_RATE", HealthConnectMapper.restingHeartRate("acc", HcRestingHr("u1", at, 58, samsung))!!.type)
        assertEquals("/min", HealthConnectMapper.respiratoryRate("acc", HcRespiratory("u1", at, 16.0, samsung))!!.unit)
        assertNull(HealthConnectMapper.restingHeartRate("acc", HcRestingHr("u2", at, 300, samsung)))
        assertNull(HealthConnectMapper.respiratoryRate("acc", HcRespiratory("u2", at, 100.0, samsung)))
    }

    @Test fun hydration_toWaterMl() {
        val row = HealthConnectMapper.hydration("acc", HcHydration("u1", at, 0.25))!!
        assertEquals("hc_water_u1", row.id)
        assertEquals(250, row.amountMl)
        assertEquals(at.toLocalDate().toString(), row.date)
        assertNull(HealthConnectMapper.hydration("acc", HcHydration("u2", at, 0.0)))
        assertNull(HealthConnectMapper.hydration("acc", HcHydration("u3", at, 9.0)))
    }

    @Test fun exercise_toActivityWithoutSteps() {
        val biking = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        val row = HealthConnectMapper.exercise("acc", HcExercise("u1", at, at.plusMinutes(45), biking, null))!!
        assertEquals("hc_ex_u1", row.id)
        assertEquals("CYCLING", row.activityType)
        assertEquals(45, row.durationMinutes)
        assertEquals(0, row.steps)
        assertNull(HealthConnectMapper.exercise("acc", HcExercise("u2", at, at, biking, null)))
        assertEquals(com.healthapp.domain.usecase.ExerciseWindow(at, at.plusMinutes(45)),
            HealthConnectMapper.exerciseWindow(HcExercise("u1", at, at.plusMinutes(45), biking, null)))
    }

    @Test fun newMappers_idsAreStable() {
        assertEquals(HealthConnectMapper.spo2("acc", HcSpo2("u1", at, 97.0, samsung)), HealthConnectMapper.spo2("acc", HcSpo2("u1", at, 97.0, samsung)))
        assertEquals(HealthConnectMapper.hydration("acc", HcHydration("u1", at, 0.3)), HealthConnectMapper.hydration("acc", HcHydration("u1", at, 0.3)))
        assertEquals("HEALTH_CONNECT:$samsung", HealthConnectMapper.bloodPressure("acc", HcBloodPressure("b1", at, 120.0, 80.0, samsung)).source)
    }

    @Test fun exercise_typesAreValidActivityTypes() {
        // Activity rows are read back with ActivityType.valueOf, so every mapped name must exist there.
        val e = androidx.health.connect.client.records.ExerciseSessionRecord
        listOf(e.EXERCISE_TYPE_RUNNING, e.EXERCISE_TYPE_BIKING, e.EXERCISE_TYPE_WALKING, e.EXERCISE_TYPE_SWIMMING_POOL,
            e.EXERCISE_TYPE_YOGA, e.EXERCISE_TYPE_STRENGTH_TRAINING, e.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            e.EXERCISE_TYPE_DANCING, e.EXERCISE_TYPE_OTHER_WORKOUT).forEach { type ->
            val row = HealthConnectMapper.exercise("acc", HcExercise("u", at, at.plusMinutes(30), type, null))!!
            com.healthapp.domain.model.ActivityType.valueOf(row.activityType)   // throws if unknown
        }
    }
}

