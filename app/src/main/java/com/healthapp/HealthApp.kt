package com.healthapp

import android.app.Application
import com.healthapp.data.reminder.ReminderWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HealthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderWorker.ensureChannel(this)
        com.healthapp.data.alerts.HealthAlertRepositoryImpl.ensureChannel(this)
    }
}
