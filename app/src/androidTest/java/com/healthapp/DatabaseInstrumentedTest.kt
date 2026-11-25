package com.healthapp

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.healthapp.data.local.HealthDatabase
import com.healthapp.data.remote.OpenFoodFactsApi
import com.healthapp.data.remote.OpenFoodFactsSearchResponse
import com.healthapp.data.repository.MoodRepositoryImpl
import com.healthapp.data.repository.NutritionRepositoryImpl
import com.healthapp.domain.model.Food
import com.healthapp.domain.model.Meal
import com.healthapp.domain.model.MealItem
import com.healthapp.domain.model.MealType
import com.healthapp.domain.repository.AccountSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/** Runs on a device/emulator against real SQLite. */
@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HealthDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @After fun cleanup() { context.deleteDatabase(dbName) }

    /** v4 -> v5 keeps existing data, adds mood_entries, drops the unused ai_analyses table; schema must match 5.json. */
    @Test fun migrate4To5_keepsDataAndMatchesSchema() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO meals (id, accountId, mealType, mealDate, totalCalories, totalProtein, totalCarbs, totalFat, totalFiber, totalSugar, totalSodium, notes, imageUri, createdAt, updatedAt) VALUES ('m1','acc','LUNCH','2026-11-20',650,32,80,22,0,0,0,NULL,NULL,'2026-11-20T12:00','2026-11-20T12:00')")
            execSQL("INSERT INTO medications (id, accountId, name, dosage, instructions, times, daysOfWeek, startDate, endDate, isActive, createdAt) VALUES ('d1','acc','Vitamin D3','1000 IU',NULL,'08:00',NULL,'2026-11-01',NULL,1,'2026-11-01T08:00')")
            execSQL("INSERT INTO ai_analyses (id, accountId, analysisType, result, createdAt) VALUES ('a1','acc','NUTRITION','{}','2026-11-01T08:00')")
            close()
        }
        // validateDroppedTables = true: fails if any table not in 5.json (e.g. ai_analyses) is still there.
        val db = helper.runMigrationsAndValidate(dbName, 5, true, HealthDatabase.MIGRATION_4_5)
        db.query("SELECT totalCalories FROM meals WHERE id = 'm1'").use { c -> assertTrue(c.moveToFirst()); assertEquals(650, c.getInt(0)) }
        db.query("SELECT name FROM medications WHERE id = 'd1'").use { c -> assertTrue(c.moveToFirst()); assertEquals("Vitamin D3", c.getString(0)) }
        db.query("SELECT COUNT(*) FROM mood_entries").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='ai_analyses'").use { c -> assertFalse(c.moveToFirst()) }
    }

    @Test fun migrate5To6_addsSupplyColumns_existingMedsNotTracked() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO medications (id, accountId, name, dosage, instructions, times, daysOfWeek, startDate, endDate, isActive, createdAt) VALUES ('d1','acc','Vitamin D3','1000 IU',NULL,'08:00',NULL,'2026-11-01',NULL,1,'2026-11-01T08:00')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 6, true, HealthDatabase.MIGRATION_5_6)
        db.query("SELECT pillsLeft, pillsPerDose FROM medications WHERE id = 'd1'").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0)); assertEquals(1, c.getInt(1))
        }
    }

    /** The app opens an old v4 file through the full migration chain without falling back to destroying data. */
    @Test fun appDatabaseOpensFromV4WithAllMigrations() {
        helper.createDatabase(dbName, 4).close()
        val db = Room.databaseBuilder(context, HealthDatabase::class.java, dbName).addMigrations(*HealthDatabase.ALL_MIGRATIONS).build()
        db.openHelper.writableDatabase.query("PRAGMA user_version").use { c -> c.moveToFirst(); assertEquals(6, c.getInt(0)) }
        db.close()
    }

    /**
     * Regression test: the meal list observes only the meals table, so a meal must become visible
     * together with its items (the bug showed "Bữa ăn · 0 g" right after saving).
     */
    /** "pho bo" (no accents, no network) finds the built-in "Phở bò", sized as one bowl. */
    @Test fun searchFoods_findsBuiltInVietnameseDish_withoutAccentsOffline() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).build()
        val noNetwork = object : OpenFoodFactsApi {
            override suspend fun searchProducts(query: String, simple: Int, action: String, json: Int, pageSize: Int, fields: String): OpenFoodFactsSearchResponse =
                throw java.io.IOException("offline")
        }
        val repo = NutritionRepositoryImpl(db.foodDao(), db.mealDao(), db.mealItemDao(), db.userProfileDao(), noNetwork, com.healthapp.data.food.VietnameseFoodLibrary(context))
        val found = repo.searchFoods("pho bo").getOrThrow()
        assertEquals("Phở bò", found.first().name)
        assertEquals(500, found.first().servingSize)
        assertEquals(450, found.first().calories) // 90 kcal/100 g × 500 g
        assertEquals("Phở bò", repo.searchFoods("PHỞ BÒ").getOrThrow().first().name)
        db.close()
    }

    @Test fun addMeal_firstEmissionAlreadyHasItems() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).build()
        val noNetwork = object : OpenFoodFactsApi {
            override suspend fun searchProducts(query: String, simple: Int, action: String, json: Int, pageSize: Int, fields: String): OpenFoodFactsSearchResponse =
                throw UnsupportedOperationException()
        }
        val repo = NutritionRepositoryImpl(db.foodDao(), db.mealDao(), db.mealItemDao(), db.userProfileDao(), noNetwork, com.healthapp.data.food.VietnameseFoodLibrary(context))
        val food = Food("f1", "Phở bò", 450, 28.0, 55.0, 12.0, 0.0, 0.0, 0, 500, "USER_CREATED")
        repo.addCustomFood(food)
        val item = MealItem("i1", food, 1.0, 500, 450, 28.0, 55.0, 12.0, 0.0, 0.0, 0)
        val today = LocalDate.now().toString()
        repo.addMeal("acc", Meal("m1", MealType.BREAKFAST, today, 450, 28.0, 55.0, 12.0, 0.0, 0.0, 0, listOf(item), createdAt = LocalDateTime.now()))
        val meals = repo.observeMealsByDate("acc", today).first { it.isNotEmpty() }
        assertEquals(listOf("Phở bò"), meals.single().items.map { it.food.name })
        db.close()
    }

    /** Check-ins saved by older versions in SharedPreferences are moved into Room once, then removed. */
    @Test fun moodRepository_importsLegacySharedPreferencesOnce() = runBlocking {
        val prefs = context.getSharedPreferences(MoodRepositoryImpl.LEGACY_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("entries", """{"2026-11-19":{"mood":"Vui","stress":3,"note":"đi dạo"},"bad-date":{"mood":"x"}}""").commit()
        val db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).build()
        val repo = MoodRepositoryImpl(context, db.moodDao(), object : AccountSession { override suspend fun getCurrentAccountId() = "acc" })
        val entries = repo.observeEntries().first()
        assertEquals(1, entries.size) // malformed date skipped
        assertEquals("Vui", entries.single().mood)
        assertEquals("đi dạo", entries.single().note)
        assertNull(prefs.getString("entries", null))
        db.close()
    }
}
