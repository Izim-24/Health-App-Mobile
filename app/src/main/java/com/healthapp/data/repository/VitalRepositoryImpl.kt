package com.healthapp.data.repository

import com.healthapp.data.local.dao.VitalLogDao
import com.healthapp.data.local.entity.VitalLogEntity
import com.healthapp.domain.model.VitalMeasurement
import com.healthapp.domain.model.VitalType
import com.healthapp.domain.repository.VitalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VitalRepositoryImpl @Inject constructor(private val dao: VitalLogDao) : VitalRepository {
    override suspend fun record(measurement: VitalMeasurement): Result<VitalMeasurement> = try {
        dao.insert(measurement.toEntity())
        Result.success(measurement)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun observeAll(userId: String): Flow<List<VitalMeasurement>> =
        dao.observeAll(userId).map { logs -> logs.map { it.toDomain() } }

    override suspend fun delete(id: String): Result<Unit> = try {
        dao.delete(id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun VitalMeasurement.toEntity() = VitalLogEntity(
        id = id,
        accountId = accountId,
        type = type.name,
        value = value,
        secondaryValue = secondaryValue,
        unit = unit,
        source = source,
        recordedAt = recordedAt
    )

    private fun VitalLogEntity.toDomain() = VitalMeasurement(
        id = id,
        accountId = accountId,
        type = VitalType.valueOf(type),
        value = value,
        secondaryValue = secondaryValue,
        unit = unit,
        source = source,
        recordedAt = recordedAt
    )
}
