package com.healthapp.data.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HealthConnectSyncPlanTest {
    private val today: LocalDate = LocalDate.of(2026, 11, 28)
    private val now: LocalDateTime = today.atTime(12, 0)
    private val lastSync: LocalDateTime = now.minusMinutes(15)

    @Test fun syncPlan_noToken_fullRead() {
        val plan = HealthConnectSyncPlan.plan(null, tokenExpired = false, lastSync = null, now = now)
        assertFalse(plan.skip)
        assertEquals(HealthConnectMapper.syncStart(null, now), plan.from)   // 30 days back
        assertTrue(plan.deleteIds.isEmpty())
    }

    @Test fun syncPlan_expiredToken_fullRead() {
        val plan = HealthConnectSyncPlan.plan(emptyList(), tokenExpired = true, lastSync = lastSync, now = now)
        assertFalse(plan.skip)
        assertEquals(HealthConnectMapper.syncStart(lastSync, now), plan.from)
    }

    @Test fun syncPlan_noChanges_skip() {
        assertTrue(HealthConnectSyncPlan.plan(emptyList(), tokenExpired = false, lastSync = lastSync, now = now).skip)
    }

    @Test fun syncPlan_upsertsReadFromStartOfEarliestDay() {
        val changes = listOf(HcChange.Upsert(today.minusDays(1).atTime(14, 0)), HcChange.Upsert(today.atTime(9, 0)))
        val plan = HealthConnectSyncPlan.plan(changes, tokenExpired = false, lastSync = lastSync, now = now)
        assertFalse(plan.skip)
        assertEquals(today.minusDays(1).atStartOfDay(), plan.from)
    }

    @Test fun syncPlan_recentUpsertStillReads2h() {
        val earlyMorning = today.atTime(1, 0)   // start of today is later than "2 hours ago"
        val plan = HealthConnectSyncPlan.plan(listOf(HcChange.Upsert(earlyMorning.minusMinutes(5))), false, lastSync, earlyMorning)
        assertEquals(earlyMorning.minusHours(2), plan.from)
    }

    @Test fun syncPlan_deletionsOnly() {
        val plan = HealthConnectSyncPlan.plan(listOf(HcChange.Deletion("abc")), false, lastSync, now)
        assertFalse(plan.skip)
        // A deletion doesn't say when the record was: re-read 30 days so hourly heart rate and daily steps are rebuilt.
        assertEquals(HealthConnectMapper.syncStart(null, now), plan.from)
        assertTrue("hc_bp_abc" in plan.deleteIds)
    }

    @Test fun deletionIds_coverAllUidPrefixes() {
        val ids = HealthConnectSyncPlan.deletionIds("abc")
        assertEquals(10, ids.size)
        assertTrue(ids.containsAll(listOf("hc_spo2_abc", "hc_water_abc", "hc_bp_abc", "hc_ex_abc", "hc_sleep_abc", "hc_w_abc")))
    }

    @Test fun syncPlan_newlyGrantedTypes_read30Days() {
        // The user just allowed SpO2/temperature…: those types have no history here yet, so go back the full 30 days.
        val plan = HealthConnectSyncPlan.plan(null, tokenExpired = false, lastSync = lastSync, now = now, newTypesGranted = true)
        assertEquals(HealthConnectMapper.syncStart(null, now), plan.from)
        val expired = HealthConnectSyncPlan.plan(emptyList(), tokenExpired = true, lastSync = lastSync, now = now, newTypesGranted = true)
        assertEquals(HealthConnectMapper.syncStart(null, now), expired.from)
    }

    @Test fun aggregateWindows_wholeDaysAndWholeHours() {
        val from = today.atTime(10, 37)
        assertEquals(today.atStartOfDay(), HealthConnectSyncPlan.stepsFrom(from))   // daily totals need the whole day
        assertEquals(today.atTime(10, 0), HealthConnectSyncPlan.heartFrom(from))     // hourly averages need the whole hour
    }
}

