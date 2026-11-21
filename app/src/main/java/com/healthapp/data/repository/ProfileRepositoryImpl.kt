package com.healthapp.data.repository

import com.healthapp.data.local.dao.UserProfileDao
import com.healthapp.data.local.entity.UserProfileEntity
import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import com.healthapp.domain.model.HealthProfile
import com.healthapp.domain.repository.ProfileRepository
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val dao: UserProfileDao
) : ProfileRepository {

    override suspend fun getProfile(accountId: String): HealthProfile? = dao.getProfileByAccountId(accountId)?.let { e ->
        HealthProfile(
            fullName = e.fullName,
            dateOfBirth = e.dateOfBirth,
            gender = runCatching { Gender.valueOf(e.gender) }.getOrNull(),
            heightCm = e.height,
            weightKg = e.weight,
            activityLevel = runCatching { ActivityLevel.valueOf(e.activityLevel) }.getOrNull(),
            targetCalories = e.targetCalories,
            targetProtein = e.targetProtein,
            targetCarbs = e.targetCarbs,
            targetFat = e.targetFat
        )
    }

    /** Upsert that keeps the row id and creation time of an existing profile. */
    override suspend fun saveProfile(accountId: String, profile: HealthProfile) {
        val existing = dao.getProfileByAccountId(accountId)
        val now = LocalDateTime.now()
        dao.insertProfile(
            UserProfileEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                accountId = accountId,
                fullName = profile.fullName,
                dateOfBirth = profile.dateOfBirth,
                gender = profile.gender?.name.orEmpty(),
                height = profile.heightCm,
                weight = profile.weightKg,
                activityLevel = profile.activityLevel?.name.orEmpty(),
                targetCalories = profile.targetCalories,
                targetProtein = profile.targetProtein,
                targetCarbs = profile.targetCarbs,
                targetFat = profile.targetFat,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }
}
