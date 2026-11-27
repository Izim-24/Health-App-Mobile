package com.healthapp.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.healthapp.domain.repository.HealthSyncRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        /** Notification extra: a navigation route to open (e.g. "health_alerts"). */
        const val EXTRA_OPEN_ROUTE = "open_route"
    }

    @Inject lateinit var healthConnect: HealthSyncRepository

    // Android 12 and older: apply the in-app language choice (no-op on 13+, where the framework does it).
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.healthapp.core.AppLocale.localized(newBase))
    }

    /** Route requested by a notification tap; consumed once navigation has happened. */
    private val openRoute = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRoute.value = intent?.getStringExtra(EXTRA_OPEN_ROUTE)
        setContent {
            HealthAppRoot(openRoute.value, onRouteOpened = { openRoute.value = null })
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_ROUTE)?.let { openRoute.value = it }
    }

    override fun onStart() {
        super.onStart()
        // Import new Health Connect data while the app is in the foreground (throttled to every 15 min).
        lifecycleScope.launch { runCatching { healthConnect.syncIfDue() } }
    }
}
