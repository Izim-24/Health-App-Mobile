package com.healthapp.presentation.ui.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthSourcesTest {
    @Test fun hcAppName_knownUnknownAndPlain() {
        assertEquals("Samsung Health", hcAppName("HEALTH_CONNECT:com.sec.android.app.shealth"))
        assertEquals("OMRON connect", hcAppName("HEALTH_CONNECT:jp.co.omron.healthcare.omron_connect"))
        assertEquals("Cooltracker", hcAppName("HEALTH_CONNECT:com.example.cooltracker"))
        assertNull(hcAppName("HEALTH_CONNECT"))
        assertNull(hcAppName("MANUAL"))
        assertNull(hcAppName(null))
    }
}
