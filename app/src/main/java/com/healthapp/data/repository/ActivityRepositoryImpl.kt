package com.healthapp.data.repository

import com.healthapp.data.local.dao.ActivityLogDao
import com.healthapp.data.local.entity.ActivityLogEntity
import com.healthapp.domain.model.ActivityRecord
import com.healthapp.domain.model.ActivityType
import com.healthapp.domain.model.ActivityIntensity
import com.healthapp.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class ActivityRepositoryImpl @Inject constructor(
    private val activityLogDao: ActivityLogDao
) : ActivityRepository {

    override suspend fun recordActivity(userId: String, activity: ActivityRecord): Result<ActivityRecord> = try {
        val entity = activity.toEntity(userId)
        activityLogDao.insertActivityLog(entity)
        Result.success(activity)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateActivity(userId: String, activity: ActivityRecord): Result<Unit> = try {
        val entity = activity.toEntity(userId)
        activityLogDao.updateActivityLog(entity)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteActivity(activityId: String): Result<Unit> = try {
        activityLogDao.deleteActivityLog(activityId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun observeActivitiesByDate(userId: String, date: String): Flow<List<ActivityRecord>> =
        activityLogDao.observeActivityLogsByDate(userId, date).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeActivities(userId: String): Flow<List<ActivityRecord>> =
        activityLogDao.observeActivityLogsByAccountId(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTotalStepsForDate(userId: String, date: String): Result<Int> = try {
        val steps = activityLogDao.getTotalStepsForDate(userId, date)
        Result.success(steps)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getTotalDurationForDate(userId: String, date: String): Result<Int> = try {
        val duration = activityLogDao.getTotalDurationForDate(userId, date)
        Result.success(duration)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getTotalCaloriesBurnedForDate(userId: String, date: String): Result<Int> = try {
        val calories = activityLogDao.getTotalCaloriesBurnedForDate(userId, date)
        Result.success(calories)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun ActivityRecord.toEntity(userId: String) = ActivityLogEntity(
        id = this.id,
        accountId = userId,
        activityType = this.activityType.name,
        durationMinutes = this.durationMinutes,
        steps = this.steps,
        distance = this.distance,
        caloriesBurned = this.caloriesBurned,
        intensity = this.intensity.name,
        recordedAt = this.recordedAt,
        date = this.date
    )

    private fun ActivityLogEntity.toDomain() = ActivityRecord(
        id = this.id,
        activityType = ActivityType.valueOf(this.activityType),
        durationMinutes = this.durationMinutes,
        steps = this.steps,
        distance = this.distance,
        caloriesBurned = this.caloriesBurned,
        intensity = ActivityIntensity.valueOf(this.intensity),
        recordedAt = this.recordedAt,
        date = this.date
    )
}
