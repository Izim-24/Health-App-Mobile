package com.healthapp.data.healthconnect

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthapp.domain.repository.HealthSyncRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Imports new Health Connect data every few hours while the app is closed.
 * Runs only when the user granted "access data in the background"; otherwise the app
 * syncs when it is opened (see [HealthConnectManager.syncIfDue]).
 */
class HealthConnectSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun healthSync(): HealthSyncRepository
    }

    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).healthSync()
        // A failed read (e.g. access revoked) is not retried in a loop; the next period tries again.
        runCatching { sync.syncIfDue() }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "health_connect_background_sync"
        const val INTERVAL_HOURS = 6L

        /** Schedules the periodic import when [enabled], cancels it otherwise. Safe to call often. */
        fun update(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
