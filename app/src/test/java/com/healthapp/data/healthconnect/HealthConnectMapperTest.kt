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
}
