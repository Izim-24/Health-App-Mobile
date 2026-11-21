package com.healthapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthapp.domain.model.AppSettings
import com.healthapp.domain.model.ThemeMode
import com.healthapp.domain.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppSettingsRepository {
    private val onboardingKey = booleanPreferencesKey("onboarding_done")
    private val themeKey = stringPreferencesKey("theme_mode")

    override val settings: Flow<AppSettings> = context.appSettingsStore.data.map { p ->
        AppSettings(
            onboardingDone = p[onboardingKey] ?: false,
            themeMode = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        )
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        context.appSettingsStore.edit { it[onboardingKey] = done }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsStore.edit { it[themeKey] = mode.name }
    }
}
