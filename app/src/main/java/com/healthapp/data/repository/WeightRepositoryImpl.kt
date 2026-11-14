package com.healthapp.data.repository

import com.healthapp.data.local.dao.WeightLogDao
import com.healthapp.data.local.entity.WeightLogEntity
import com.healthapp.domain.model.WeightRecord
import com.healthapp.domain.repository.WeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class WeightRepositoryImpl @Inject constructor(
    private val weightLogDao: WeightLogDao
) : WeightRepository {

    override suspend fun recordWeight(userId: String, weight: WeightRecord): Result<WeightRecord> = try {
        val entity = weight.toEntity(userId)
        weightLogDao.insertWeightLog(entity)
        Result.success(weight)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateWeight(userId: String, weight: WeightRecord): Result<Unit> = try {
        val entity = weight.toEntity(userId)
        weightLogDao.updateWeightLog(entity)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteWeight(weightId: String): Result<Unit> = try {
        weightLogDao.deleteWeightLog(weightId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getWeightForDate(userId: String, date: String): Result<WeightRecord?> = try {
        val entity = weightLogDao.getWeightLogByDate(userId, date)
        Result.success(entity?.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun observeWeightRecords(userId: String): Flow<List<WeightRecord>> =
        weightLogDao.observeWeightLogsByAccountId(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getWeightTrend(userId: String, days: Int): Result<List<WeightRecord>> = try {
        val logs = weightLogDao.getRecentWeightLogs(userId, days)
        Result.success(logs.map { it.toDomain() })
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun WeightRecord.toEntity(userId: String) = WeightLogEntity(
        id = this.id,
        accountId = userId,
        weight = this.weight,
        recordedAt = this.recordedAt,
        date = this.date
    )

    private fun WeightLogEntity.toDomain() = WeightRecord(
        id = this.id,
        weight = this.weight,
        recordedAt = this.recordedAt,
        date = this.date
    )
}
