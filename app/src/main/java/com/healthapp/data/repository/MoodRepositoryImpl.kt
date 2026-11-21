package com.healthapp.data.repository

import android.content.Context
import com.healthapp.data.local.dao.MoodDao
import com.healthapp.data.local.entity.MoodEntity
import com.healthapp.domain.model.MoodEntry
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.MoodRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MoodDao,
    private val session: AccountSession
) : MoodRepository {
    private val importLock = Mutex()

    override fun observeEntries(): Flow<List<MoodEntry>> = flow {
        val accountId = session.getCurrentAccountId()
        importLegacyEntries(accountId)
        emitAll(dao.observeEntries(accountId).map { list -> list.mapNotNull { it.toDomain() } })
    }

    override suspend fun save(entry: MoodEntry): Result<Unit> = runCatching {
        require(entry.mood.isNotBlank()) { "Chọn một cảm xúc." }
        require(entry.stress in 0..10) { "Mức căng thẳng cần từ 0 đến 10." }
        val accountId = session.getCurrentAccountId()
        dao.upsert(entry.toEntity(accountId))
    }

    /**
     * Before v5, check-ins were stored as JSON in SharedPreferences. Copy them into Room once,
     * then remove the old copy, so upgrading users keep their history.
     */
    private suspend fun importLegacyEntries(accountId: String) = importLock.withLock {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(LEGACY_KEY, null) ?: return@withLock
        val json = runCatching { JSONObject(raw) }.getOrNull()
        if (json != null) {
            val entries = json.keys().asSequence().mapNotNull { key ->
                val v = json.optJSONObject(key) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
                MoodEntry(date, v.optString("mood"), v.optInt("stress").coerceIn(0, 10), v.optString("note"), LocalDateTime.now())
                    .takeIf { it.mood.isNotBlank() }?.toEntity(accountId)
            }.toList()
            if (entries.isNotEmpty()) dao.upsertAll(entries)
        }
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    companion object {
        const val LEGACY_PREFS = "mental_health_private"
        private const val LEGACY_KEY = "entries"

        fun MoodEntry.toEntity(accountId: String) = MoodEntity(
            id = "$accountId|$date", accountId = accountId, date = date.toString(),
            mood = mood, stress = stress, note = note, updatedAt = updatedAt
        )

        fun MoodEntity.toDomain(): MoodEntry? = runCatching {
            MoodEntry(LocalDate.parse(date), mood, stress, note, updatedAt)
        }.getOrNull()
    }
}
