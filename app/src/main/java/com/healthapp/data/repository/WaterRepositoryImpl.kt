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
    private val waterLogDao: WaterLogDao
) : WaterRepository {

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
