package com.healthapp.data.repository

import com.healthapp.data.local.dao.SleepLogDao
import com.healthapp.data.local.entity.SleepLogEntity
import com.healthapp.domain.model.SleepRecord
import com.healthapp.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

class SleepRepositoryImpl @Inject constructor(
    private val sleepLogDao: SleepLogDao
) : SleepRepository {

    override suspend fun recordSleep(userId: String, sleep: SleepRecord): Result<SleepRecord> = try {
        val entity = sleep.toEntity(userId)
        sleepLogDao.insertSleepLog(entity)
        Result.success(sleep)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateSleep(userId: String, sleep: SleepRecord): Result<Unit> = try {
        val entity = sleep.toEntity(userId)
        sleepLogDao.updateSleepLog(entity)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteSleep(sleepId: String): Result<Unit> = try {
        sleepLogDao.deleteSleepLog(sleepId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getSleepForDate(userId: String, date: String): Result<SleepRecord?> = try {
        val entity = sleepLogDao.getSleepLogByDate(userId, date)
        Result.success(entity?.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun observeSleepRecords(userId: String): Flow<List<SleepRecord>> =
        sleepLogDao.observeSleepLogsByAccountId(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getAverageSleepDuration(userId: String, days: Int): Result<Int> = try {
        val now = LocalDateTime.now()
        val endDate = now.toLocalDate().toString()
        val startDate = endDate // Simplified - would need date arithmetic

        val logs = sleepLogDao.getSleepLogsInRange(userId, startDate, endDate)
        val avgDuration = if (logs.isNotEmpty()) {
            logs.sumOf { it.durationMinutes } / logs.size
        } else {
            0
        }
        Result.success(avgDuration)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun SleepRecord.toEntity(userId: String) = SleepLogEntity(
        id = this.id,
        accountId = userId,
        bedtime = this.bedtime,
        wakeupTime = this.wakeupTime,
        durationMinutes = this.durationMinutes,
        sleepQuality = this.sleepQuality,
        notes = this.notes,
        date = this.date
    )

    private fun SleepLogEntity.toDomain() = SleepRecord(
        id = this.id,
        bedtime = this.bedtime,
        wakeupTime = this.wakeupTime,
        durationMinutes = this.durationMinutes,
        sleepQuality = this.sleepQuality,
        notes = this.notes,
        date = this.date
    )
}
