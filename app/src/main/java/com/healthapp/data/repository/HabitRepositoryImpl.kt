package com.healthapp.data.repository

import com.healthapp.data.local.dao.HabitDao
import com.healthapp.data.local.dao.HabitLogDao
import com.healthapp.data.local.entity.HabitEntity
import com.healthapp.data.local.entity.HabitLogEntity
import com.healthapp.domain.model.Habit
import com.healthapp.domain.model.HabitFrequency
import com.healthapp.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min

class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao
) : HabitRepository {

    override suspend fun createHabit(habit: Habit): Result<Habit> = try {
        val entity = habit.toEntity()
        habitDao.insertHabit(entity)
        Result.success(habit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateHabit(habit: Habit): Result<Unit> = try {
        val entity = habit.toEntity()
        habitDao.updateHabit(entity)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteHabit(habitId: String): Result<Unit> = try {
        habitDao.deleteHabit(habitId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getHabit(habitId: String): Result<Habit> = try {
        val entity = habitDao.getHabitById(habitId)
        if (entity != null) {
            Result.success(entity.toDomain())
        } else {
            Result.failure(Exception("Habit not found"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun observeHabits(userId: String): Flow<List<Habit>> =
        habitDao.observeHabitsByAccountId(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun markHabitComplete(habitId: String, date: String): Result<Unit> = try {
        setHabitCompleted(habitId, date, true).getOrThrow()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun setHabitCompleted(habitId: String, date: String, completed: Boolean): Result<Unit> = try {
        val existingLog = habitLogDao.getHabitLog(habitId, date)
        val now = LocalDateTime.now()

        if (existingLog != null) {
            habitLogDao.updateHabitLog(existingLog.copy(status = completed, recordedAt = now))
        } else {
            habitLogDao.insertHabitLog(
                HabitLogEntity(
                    id = UUID.randomUUID().toString(),
                    habitId = habitId,
                    completionDate = date,
                    status = completed,
                    recordedAt = now
                )
            )
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getCompletedHabitIdsForDate(userId: String, date: String): Result<Set<String>> = try {
        Result.success(habitLogDao.getCompletedHabitIds(userId, date).toSet())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getHabitStreak(habitId: String): Result<Int> = try {
        var streak = 0
        val habit = habitDao.getHabitById(habitId) ?: return Result.success(0)

        // Calculate consecutive days
        val today = LocalDateTime.now().toLocalDate().toString()
        var checkDate = today

        while (true) {
            val log = habitLogDao.getHabitLog(habitId, checkDate)
            if (log != null && log.status) {
                streak++
                // Move to previous day
                checkDate = decrementDate(checkDate)
            } else {
                break
            }
        }

        Result.success(streak)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getHabitCompletionRate(habitId: String, days: Int): Result<Double> = try {
        val endDate = LocalDateTime.now().toLocalDate().toString()
        val startDate = LocalDate.parse(endDate).minusDays((days - 1).coerceAtLeast(0).toLong()).toString()

        val completedDays = habitLogDao.getCompletedCountInRange(habitId, startDate, endDate)
        val rate = completedDays.toDouble() / days.toDouble()

        Result.success(rate)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getCompletedHabitsForDate(userId: String, date: String): Result<Int> = try {
        val count = habitLogDao.getCompletedHabitsCount(userId, date)
        Result.success(count)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getTotalActiveHabits(userId: String): Result<Int> = try {
        val count = habitLogDao.getTotalActiveHabits(userId)
        Result.success(count)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Habit.toEntity() = HabitEntity(
        id = this.id,
        accountId = this.accountId,
        name = this.name,
        description = this.description,
        frequency = this.frequency.name,
        daysOfWeek = null, // TODO: Convert list to JSON
        reminderTime = this.reminderTime,
        goalValue = this.goalValue,
        goalUnit = this.goalUnit,
        isEnabled = this.isEnabled,
        createdAt = this.createdAt,
        updatedAt = LocalDateTime.now()
    )

    private fun HabitEntity.toDomain() = Habit(
        id = this.id,
        accountId = this.accountId,
        name = this.name,
        description = this.description,
        frequency = HabitFrequency.valueOf(this.frequency),
        daysOfWeek = null, // TODO: Parse from JSON
        reminderTime = this.reminderTime,
        isEnabled = this.isEnabled,
        streak = 0, // Calculated separately
        completionRate = 0.0, // Calculated separately
        createdAt = this.createdAt,
        goalValue = this.goalValue,
        goalUnit = this.goalUnit
    )

    private fun decrementDate(date: String): String {
        return LocalDate.parse(date).minusDays(1).toString()
    }

    private fun decrementDateByDays(date: String, days: Int): String {
        return LocalDate.parse(date).minusDays(days.toLong()).toString()
    }
}
