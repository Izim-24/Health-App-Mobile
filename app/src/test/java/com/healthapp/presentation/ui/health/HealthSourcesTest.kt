package com.healthapp.presentation.ui.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSourcesTest {
    @Test fun hcAppName_knownAppsOnly() {
        assertEquals("Samsung Health", hcAppName("HEALTH_CONNECT:com.sec.android.app.shealth"))
        assertEquals("OMRON connect", hcAppName("HEALTH_CONNECT:jp.co.omron.healthcare.omron_connect"))
        // Unknown apps: no guessed name from the package (it reads like "Mobile"); the UI shows plain "Health Connect".
        assertNull(hcAppName("HEALTH_CONNECT:com.example.cooltracker"))
        assertNull(hcAppName("HEALTH_CONNECT:com.healthapp"))
        assertNull(hcAppName("HEALTH_CONNECT"))
        assertNull(hcAppName("MANUAL"))
        assertNull(hcAppName(null))
    }

    @Test fun isOwnSample_onlyThisAppsDebugWriter() {
        assertTrue(isOwnSample("HEALTH_CONNECT:com.healthapp"))
        assertFalse(isOwnSample("HEALTH_CONNECT:com.sec.android.app.shealth"))
        assertFalse(isOwnSample("DEMO"))
    }
}
