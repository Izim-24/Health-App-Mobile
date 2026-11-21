package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.model.HealthProfile
import com.healthapp.domain.repository.ProfileRepository
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import com.healthapp.domain.usecase.HealthCalculations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = true,
    val name: String = "",
    val birthDate: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val calorieGoal: String = "",
    val gender: Gender? = null,
    val activityLevel: ActivityLevel? = null,
    val proteinGoal: Double = 0.0,
    val carbsGoal: Double = 0.0,
    val fatGoal: Double = 0.0,
    val suggestion: String? = null,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(private val accountManager: AccountSession, private val profiles: ProfileRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state = mutable.asStateFlow()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        runCatching { profiles.getProfile(accountManager.getCurrentAccountId()) }
            .onSuccess { profile ->
                mutable.value = ProfileUiState(
                    loading = false,
                    name = profile?.fullName.orEmpty(),
                    birthDate = profile?.dateOfBirth.orEmpty(),
                    heightCm = profile?.heightCm?.takeIf { it > 0 }?.toString().orEmpty(),
                    weightKg = profile?.weightKg?.takeIf { it > 0 }?.toString().orEmpty(),
                    calorieGoal = profile?.targetCalories?.takeIf { it > 0 }?.toString().orEmpty(),
                    gender = profile?.gender,
                    activityLevel = profile?.activityLevel,
                    proteinGoal = profile?.targetProtein ?: 0.0,
                    carbsGoal = profile?.targetCarbs ?: 0.0,
                    fatGoal = profile?.targetFat ?: 0.0
                )
            }.onFailure { mutable.value = mutable.value.copy(loading = false, error = it.message) }
    }

    fun update(name: String, birthDate: String, heightCm: String, weightKg: String, calorieGoal: String) {
        mutable.update { it.copy(name = name, birthDate = birthDate, heightCm = heightCm, weightKg = weightKg, calorieGoal = calorieGoal, saved = false, error = null) }
    }

    fun setGender(gender: Gender) = mutable.update { it.copy(gender = gender, saved = false) }
    fun setActivityLevel(level: ActivityLevel) = mutable.update { it.copy(activityLevel = level, saved = false) }

    /** Fills calorie + macro goals from Mifflin–St Jeor; the user can still edit before saving. */
    fun calculateTargets() {
        val s = mutable.value
        val birth = runCatching { LocalDate.parse(s.birthDate.trim()) }.getOrNull()
        val age = birth?.let { HealthCalculations.ageOn(it) }
        val height = s.heightCm.toIntOrNull()
        val weight = s.weightKg.replace(',', '.').toDoubleOrNull()
        if (age == null || height == null || weight == null || s.gender == null || s.activityLevel == null) {
            mutable.update { it.copy(error = "Cần ngày sinh, chiều cao, cân nặng, giới tính và mức vận động để tính mục tiêu.") }
            return
        }
        val targets = HealthCalculations.energyTargets(s.gender, weight, height, age, s.activityLevel)
        if (targets == null) {
            mutable.update { it.copy(error = "Thông tin nằm ngoài khoảng tính toán được (tuổi 15–110, cao 100–250 cm).") }
            return
        }
        mutable.update {
            it.copy(
                calorieGoal = targets.tdee.toString(),
                proteinGoal = targets.proteinGrams,
                carbsGoal = targets.carbsGrams,
                fatGoal = targets.fatGrams,
                suggestion = "BMR ${targets.bmr} kcal · duy trì ${targets.tdee} kcal/ngày · P ${targets.proteinGrams.toInt()}g · C ${targets.carbsGrams.toInt()}g · F ${targets.fatGrams.toInt()}g. Ước tính theo công thức, không thay thế tư vấn chuyên môn.",
                error = null,
                saved = false
            )
        }
    }

    fun save() {
        val input = mutable.value
        val height = input.heightCm.toIntOrNull() ?: 0
        val weight = input.weightKg.replace(',', '.').toDoubleOrNull() ?: 0.0
        val calories = input.calorieGoal.toIntOrNull() ?: 0
        val birthValid = input.birthDate.isBlank() || runCatching { LocalDate.parse(input.birthDate.trim()).isBefore(LocalDate.now().plusDays(1)) }.getOrDefault(false)
        if (!birthValid || (input.heightCm.isNotBlank() && height !in 50..250) || (input.weightKg.isNotBlank() && weight !in 2.0..500.0) || (input.calorieGoal.isNotBlank() && calories !in 500..10000)) {
            mutable.value = input.copy(error = "Kiểm tra ngày sinh (YYYY-MM-DD), chiều cao, cân nặng và mục tiêu calo.")
            return
        }
        viewModelScope.launch {
            runCatching {
                profiles.saveProfile(
                    accountManager.getCurrentAccountId(),
                    HealthProfile(
                        fullName = input.name.trim(), dateOfBirth = input.birthDate.trim(), gender = input.gender,
                        heightCm = height, weightKg = weight, activityLevel = input.activityLevel,
                        targetCalories = calories, targetProtein = input.proteinGoal, targetCarbs = input.carbsGoal, targetFat = input.fatGoal
                    )
                )
            }.onSuccess { mutable.update { it.copy(saved = true, error = null) } }
                .onFailure { e -> mutable.update { it.copy(error = e.message ?: "Không thể lưu hồ sơ") } }
        }
    }
}
