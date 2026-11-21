package com.healthapp.data.repository

import com.healthapp.data.local.dao.WaterLogDao
import com.healthapp.data.local.entity.WaterLogEntity
import com.healthapp.domain.model.WaterIntake
import com.healthapp.domain.repository.WaterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

class WaterRepositoryImpl @Inject constructor(
    private val waterLogDao: WaterLogDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : WaterRepository {
    // A single number per account: SharedPreferences is enough (and is what earlier versions used).
    private val goalPrefs get() = context.getSharedPreferences(GOAL_PREFS, android.content.Context.MODE_PRIVATE)

    override suspend fun getDailyGoal(userId: String): Int = goalPrefs.getInt("goal_$userId", 0)

    override suspend fun setDailyGoal(userId: String, ml: Int) {
        require(ml in 100..10000) { "Mục tiêu cần từ 100 đến 10000 ml." }
        goalPrefs.edit().putInt("goal_$userId", ml).apply()
    }

    companion object { const val GOAL_PREFS = "water_goals" }

    override suspend fun addWaterIntake(userId: String, amountMl: Int): Result<WaterIntake> = try {
        val now = LocalDateTime.now()
        val today = now.toLocalDate().toString()

        val entity = WaterLogEntity(
            id = UUID.randomUUID().toString(),
            accountId = userId,
            amountMl = amountMl,
            recordedAt = now,
            date = today
        )

        waterLogDao.insertWaterLog(entity)
        Result.success(entity.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getTotalWaterForDate(userId: String, date: String): Result<Int> = try {
        val total = waterLogDao.getTotalWaterForDate(userId, date)
        Result.success(total)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun observeWaterLogsForDate(userId: String, date: String): Flow<List<WaterIntake>> =
        waterLogDao.observeWaterLogsByDate(userId, date).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun deleteWaterLog(logId: String): Result<Unit> = try {
        waterLogDao.deleteWaterLog(logId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun WaterLogEntity.toDomain() = WaterIntake(
        id = this.id,
        amountMl = this.amountMl,
        recordedAt = this.recordedAt,
        date = this.date
    )
}
