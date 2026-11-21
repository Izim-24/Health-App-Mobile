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
    @Inject lateinit var healthConnect: HealthSyncRepository

    // Android 12 and older: apply the in-app language choice (no-op on 13+, where the framework does it).
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.healthapp.core.AppLocale.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthAppRoot()
        }
    }

    override fun onStart() {
        super.onStart()
        // Import new Health Connect data while the app is in the foreground (throttled to every 15 min).
        lifecycleScope.launch { runCatching { healthConnect.syncIfDue() } }
    }
}
