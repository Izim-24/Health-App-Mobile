package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import com.healthapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import com.healthapp.domain.model.HealthProfile
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.AppSettingsRepository
import com.healthapp.domain.repository.ProfileRepository
import com.healthapp.domain.repository.WaterRepository
import com.healthapp.domain.usecase.EnergyTargets
import com.healthapp.domain.usecase.HealthCalculations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,                 // 0 welcome · 1 about you · 2 body · 3 goals
    val name: String = "",
    val birthDate: LocalDate? = null,
    val gender: Gender? = null,
    val heightCm: String = "",
    val weightKg: String = "",
    val activity: ActivityLevel? = null,
    val targets: EnergyTargets? = null,
    val calorieGoal: String = "",
    val waterGoalMl: Int? = null,
    val bmi: Double? = null,
    val error: UiText? = null,
    val saving: Boolean = false,
    val finished: Boolean = false
) {
    companion object { const val LAST_STEP = 3 }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val session: AccountSession,
    private val profiles: ProfileRepository,
    private val water: WaterRepository,
    private val settings: AppSettingsRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(OnboardingUiState())
    val state = mutable.asStateFlow()

    fun setName(v: String) = mutable.update { it.copy(name = v.take(60), error = null) }
    fun setBirthDate(v: LocalDate) = mutable.update { it.copy(birthDate = v, error = null) }
    fun setGender(v: Gender) = mutable.update { it.copy(gender = v, error = null) }
    fun setHeight(v: String) = mutable.update { it.copy(heightCm = v.filter(Char::isDigit).take(3), error = null) }
    fun setWeight(v: String) = mutable.update { it.copy(weightKg = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5), error = null) }
    fun setActivity(v: ActivityLevel) = mutable.update { it.copy(activity = v, error = null) }
    fun setCalorieGoal(v: String) = mutable.update { it.copy(calorieGoal = v.filter(Char::isDigit).take(4), error = null) }

    private fun weight(s: OnboardingUiState) = s.weightKg.replace(',', '.').toDoubleOrNull()

    /** Returns an error for the current step, or null if the user may continue. */
    private fun validate(s: OnboardingUiState): UiText? = when (s.step) {
        1 -> when {
            s.name.isBlank() -> UiText.Res(R.string.err_enter_name)
            s.birthDate == null -> UiText.Res(R.string.err_pick_birth)
            (HealthCalculations.ageOn(s.birthDate) ?: -1) !in 15..110 -> UiText.Res(R.string.err_age)
            s.gender == null -> UiText.Res(R.string.err_pick_gender)
            else -> null
        }
        2 -> when {
            (s.heightCm.toIntOrNull() ?: 0) !in 100..250 -> UiText.Res(R.string.err_height_range)
            (weight(s) ?: 0.0) !in 20.0..400.0 -> UiText.Res(R.string.err_weight_range)
            s.activity == null -> UiText.Res(R.string.err_pick_activity)
            else -> null
        }
        3 -> if ((s.calorieGoal.toIntOrNull() ?: 0) !in 800..6000) UiText.Res(R.string.err_energy_range) else null
        else -> null
    }

    fun next() {
        val s = mutable.value
        validate(s)?.let { e -> mutable.update { it.copy(error = e) }; return }
        if (s.step == OnboardingUiState.LAST_STEP) { finish(); return }
        val nextStep = s.step + 1
        mutable.update { cur ->
            if (nextStep == 3) {
                // Entering "goals": compute targets from what the user entered.
                val w = weight(cur)!!; val h = cur.heightCm.toInt()
                val age = HealthCalculations.ageOn(cur.birthDate!!)!!
                val t = HealthCalculations.energyTargets(cur.gender!!, w, h, age, cur.activity!!)
                cur.copy(
                    step = nextStep, targets = t, calorieGoal = t?.tdee?.toString().orEmpty(),
                    waterGoalMl = HealthCalculations.suggestedWaterMl(w), bmi = HealthCalculations.bmi(h, w), error = null
                )
            } else cur.copy(step = nextStep, error = null)
        }
    }

    fun back() = mutable.update { if (it.step > 0) it.copy(step = it.step - 1, error = null) else it }

    /** Skipping keeps an empty profile; the user can fill it later in Hồ sơ. */
    fun skip() {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            mutable.update { it.copy(finished = true) }
        }
    }

    private fun finish() {
        val s = mutable.value
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                val accountId = session.getCurrentAccountId()
                val t = s.targets
                profiles.saveProfile(
                    accountId,
                    HealthProfile(
                        fullName = s.name.trim(), dateOfBirth = s.birthDate.toString(), gender = s.gender,
                        heightCm = s.heightCm.toInt(), weightKg = weight(s)!!, activityLevel = s.activity,
                        targetCalories = s.calorieGoal.toInt(),
                        targetProtein = t?.proteinGrams ?: 0.0, targetCarbs = t?.carbsGrams ?: 0.0, targetFat = t?.fatGrams ?: 0.0
                    )
                )
                s.waterGoalMl?.let { water.setDailyGoal(accountId, it) }
                settings.setOnboardingDone(true)
            }.onSuccess { mutable.update { it.copy(saving = false, finished = true) } }
                .onFailure { e -> mutable.update { it.copy(saving = false, error = e.message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.err_save_profile)) } }
        }
    }
}
