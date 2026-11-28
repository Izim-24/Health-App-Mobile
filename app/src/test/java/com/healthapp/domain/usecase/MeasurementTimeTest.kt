package com.healthapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MeasurementTimeTest {
    private val now = LocalDateTime.of(2026, 11, 28, 12, 40, 30)

    @Test fun today_atTypedTime() {
        assertEquals(LocalDateTime.of(2026, 11, 28, 7, 5), MeasurementTime.resolve(0, "7:05", now))
        assertEquals(LocalDateTime.of(2026, 11, 28, 12, 40), MeasurementTime.resolve(0, "12:40", now))   // "now", prefilled
    }

    @Test fun yesterday_anyTime() {
        assertEquals(LocalDateTime.of(2026, 11, 27, 22, 15), MeasurementTime.resolve(1, "22:15", now))
    }

    @Test fun rejectsFutureBadFormatAndTooOld() {
        assertNull(MeasurementTime.resolve(0, "13:30", now))      // later today: not measured yet
        assertNull(MeasurementTime.resolve(0, "25:00", now))
        assertNull(MeasurementTime.resolve(0, "", now))
        assertNull(MeasurementTime.resolve(MeasurementTime.MAX_DAYS_AGO + 1, "08:00", now))
    }

    @Test fun format_isHourMinute() {
        assertEquals("09:05", MeasurementTime.format(LocalDateTime.of(2026, 11, 28, 9, 5, 59)))
    }

    @Test fun acceptsWhatANumberKeyboardCanType() {
        // Android's number keyboard has no ":", so "." "," "h" and plain digits are accepted too.
        val seven30 = LocalDateTime.of(2026, 11, 28, 7, 30)
        listOf("07:30", "7.30", "7,30", "7h30", "0730", "730").forEach { assertEquals(it, seven30, MeasurementTime.resolve(0, it, now)) }
        assertNull(MeasurementTime.resolve(0, "7", now))
        assertNull(MeasurementTime.resolve(0, "07300", now))
    }
}

