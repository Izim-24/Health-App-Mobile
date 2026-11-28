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

    // ---- review fixes ----

    @Test fun syncPlan_oldUpsert_clampedTo30Days() {
        // A source app back-filling a year of history must not trigger an unbounded read.
        val plan = HealthConnectSyncPlan.plan(listOf(HcChange.Upsert(now.minusDays(400))), false, lastSync, now)
        assertEquals(HealthConnectMapper.syncStart(null, now), plan.from)
    }

    @Test fun syncPlan_newTypesWithValidToken_keepsDeletions() {
        // Granting SpO2 must not lose a deletion the old token already reported.
        val plan = HealthConnectSyncPlan.plan(listOf(HcChange.Deletion("bp1")), false, lastSync, now, newTypesGranted = true)
        assertEquals(HealthConnectMapper.syncStart(null, now), plan.from)
        assertTrue("hc_bp_bp1" in plan.deleteIds)
    }

    @Test fun tokenToSave_keepsOldWhenChangesFailed() {
        assertEquals("old", HealthConnectSyncPlan.tokenToSave(old = "old", next = "new", changesFailed = true))
        assertEquals("new", HealthConnectSyncPlan.tokenToSave(old = "old", next = "new", changesFailed = false))
        assertEquals("new", HealthConnectSyncPlan.tokenToSave(old = null, next = "new", changesFailed = false))
    }

    @Test fun pending_carriesFailedTypesAndEarliestFrom() {
        val first = HealthConnectSyncPlan.pending(previous = null, failed = setOf("SPO2"), granted = setOf("SPO2", "BP"), from = now.minusHours(3))!!
        assertEquals(setOf("SPO2"), first.types)
        assertEquals(now.minusHours(3), first.from)
        // Still failing on the next sync: keep the earliest start so nothing in between is skipped.
        val again = HealthConnectSyncPlan.pending(first, failed = setOf("SPO2"), granted = setOf("SPO2", "BP"), from = now.minusHours(1))!!
        assertEquals(now.minusHours(3), again.from)
        // Read fine now: nothing pending. Permission withdrawn: dropped too.
        assertEquals(null, HealthConnectSyncPlan.pending(first, failed = emptySet(), granted = setOf("SPO2", "BP"), from = now))
        assertEquals(null, HealthConnectSyncPlan.pending(first, failed = setOf("SPO2"), granted = setOf("BP"), from = now))
    }

    @Test fun fromFor_pendingTypeReadsFromEarlier() {
        val pending = HcPending(setOf("SPO2"), now.minusDays(2))
        assertEquals(now.minusDays(2), HealthConnectSyncPlan.fromFor("SPO2", now.minusHours(2), pending, now))
        assertEquals(now.minusHours(2), HealthConnectSyncPlan.fromFor("BP", now.minusHours(2), pending, now))
        // Never earlier than the 30-day window.
        assertEquals(HealthConnectMapper.syncStart(null, now),
            HealthConnectSyncPlan.fromFor("SPO2", now, HcPending(setOf("SPO2"), now.minusDays(90)), now))
    }

    @Test fun staleIds_storedButNoLongerInHealthConnect() {
        val stored = listOf("hc_bp_old", "hc_bp_keep", "hc_bp_new")
        assertEquals(listOf("hc_bp_old"), HealthConnectSyncPlan.staleIds(stored, returned = setOf("hc_bp_keep", "hc_bp_new", "hc_bp_extra")))
    }
}
