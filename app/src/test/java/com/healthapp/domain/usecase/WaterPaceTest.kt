package com.healthapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WaterPaceTest {
    private fun at(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test fun expectedRisesEvenlyOverTheWakingDay() {
        assertEquals(0, WaterPace.expectedBy(2000, at(6, 30)))      // before 07:00
        assertEquals(1000, WaterPace.expectedBy(2000, at(14, 30)))  // halfway through 07:00–22:00
        assertEquals(2000, WaterPace.expectedBy(2000, at(22)))
        assertEquals(2000, WaterPace.expectedBy(2000, at(23, 30)))
    }

    @Test fun reminderSkippedWhenOnPace_firesWhenBehind() {
        assertFalse(WaterPace.shouldRemind(1100, 2000, at(14, 30)))  // ahead of the 1000 ml pace
        assertTrue(WaterPace.shouldRemind(600, 2000, at(14, 30)))
        assertEquals(400, WaterPace.behindBy(600, 2000, at(14, 30)))
        assertFalse(WaterPace.shouldRemind(2000, 2000, at(23)))      // goal reached
    }

    @Test fun withoutAGoalEveryReminderFires() {
        assertNull(WaterPace.behindBy(0, 0, at(10)))
        assertTrue(WaterPace.shouldRemind(5000, 0, at(10)))
    }
}
