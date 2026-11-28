package com.healthapp.data.local

import com.healthapp.domain.model.ActivityType
import com.healthapp.domain.model.VitalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnumParsingTest {
    @Test fun enumOrNull_knownAndUnknownNames() {
        assertEquals(VitalType.OXYGEN_SATURATION, enumOrNull<VitalType>("OXYGEN_SATURATION"))
        assertNull(enumOrNull<VitalType>("BLOOD_OXYGEN"))   // a row written by a newer or older version
        assertNull(enumOrNull<VitalType>(null))
    }

    @Test fun enumOr_fallsBackInsteadOfThrowing() {
        assertEquals(ActivityType.RUNNING, enumOr("RUNNING", ActivityType.OTHER))
        assertEquals(ActivityType.OTHER, enumOr("GYM", ActivityType.OTHER))   // an old imported row
    }
}
