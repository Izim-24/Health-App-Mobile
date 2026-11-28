package com.healthapp.presentation.ui.health

import com.healthapp.domain.model.VitalMeasurement
import com.healthapp.domain.model.VitalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class VitalHistoryTest {
    private val t0 = LocalDateTime.of(2026, 11, 28, 12, 0)
    private fun v(id: String, type: VitalType, hoursAgo: Long) =
        VitalMeasurement(id, "a", type, 70.0, if (type == VitalType.BLOOD_PRESSURE) 80.0 else null, "x", "MANUAL", t0.minusHours(hoursAgo))

    // 30 hourly heart-rate imports, plus one blood-pressure reading older than all of them.
    private val vitals = (0 until 30).map { v("hr$it", VitalType.HEART_RATE, it.toLong()) } + v("bp", VitalType.BLOOD_PRESSURE, 40)

    @Test fun newestFirst_limited_withMoreFlag() {
        val page = vitalHistory(vitals, type = null, limit = 20)
        assertEquals(20, page.items.size)
        assertEquals("hr0", page.items.first().id)
        assertTrue(page.hasMore)
    }

    @Test fun typeFilter_reachesOlderReadingsHiddenByImports() {
        val page = vitalHistory(vitals, type = VitalType.BLOOD_PRESSURE, limit = 20)
        assertEquals(listOf("bp"), page.items.map { it.id })
        assertFalse(page.hasMore)
    }

    @Test fun showMore_revealsTheRest() {
        val page = vitalHistory(vitals, type = null, limit = 40)
        assertEquals(31, page.items.size)
        assertEquals("bp", page.items.last().id)
        assertFalse(page.hasMore)
    }
}
