package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.model.ThemeMode
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.AppSettingsRepository
import com.healthapp.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-level state: which screen to start on, and the theme. */
data class AppUiState(
    val ready: Boolean = false,
    val startDestination: String = "dashboard",
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    private val profiles: ProfileRepository,
    private val session: AccountSession
) : ViewModel() {
    private val mutable = MutableStateFlow(AppUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val initial = settings.settings.first()
            var done = initial.onboardingDone
            if (!done) {
                // Users who already filled a profile before onboarding existed should not see it.
                val p = runCatching { profiles.getProfile(session.getCurrentAccountId()) }.getOrNull()
                if (p != null && (p.fullName.isNotBlank() || p.heightCm > 0)) {
                    settings.setOnboardingDone(true); done = true
                }
            }
            mutable.update { it.copy(ready = true, startDestination = if (done) "dashboard" else "onboarding", themeMode = initial.themeMode) }
            // Keep following theme changes made in Settings.
            settings.settings.collect { s -> mutable.update { it.copy(themeMode = s.themeMode) } }
        }
    }
}
