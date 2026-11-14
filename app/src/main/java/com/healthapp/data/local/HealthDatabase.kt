package com.healthapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.healthapp.data.local.converter.Converters
import com.healthapp.data.local.dao.*
import com.healthapp.data.local.entity.*

@Database(
    entities = [
        AccountEntity::class,
        UserProfileEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        FoodEntity::class,
        MealEntity::class,
        MealItemEntity::class,
        WaterLogEntity::class,
        SleepLogEntity::class,
        ActivityLogEntity::class,
        WeightLogEntity::class,
        VitalLogEntity::class,
        ReminderEntity::class,
        AIAnalysisEntity::class,
        MedicationEntity::class,
        MedicationLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun mealItemDao(): MealItemDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun sleepLogDao(): SleepLogDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun vitalLogDao(): VitalLogDao
    abstract fun reminderDao(): ReminderDao
    abstract fun aiAnalysisDao(): AIAnalysisDao
    abstract fun dataManagementDao(): DataManagementDao
    abstract fun medicationDao(): MedicationDao
    abstract fun healthConnectDao(): HealthConnectDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE habits ADD COLUMN goalValue REAL")
                database.execSQL("ALTER TABLE habits ADD COLUMN goalUnit TEXT")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS vital_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        accountId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        value REAL NOT NULL,
                        secondaryValue REAL,
                        unit TEXT NOT NULL,
                        source TEXT NOT NULL,
                        recordedAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vital_logs_accountId_type_recordedAt ON vital_logs(accountId, type, recordedAt)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS medications (
                        id TEXT NOT NULL PRIMARY KEY,
                        accountId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        dosage TEXT NOT NULL,
                        instructions TEXT,
                        times TEXT NOT NULL,
                        daysOfWeek TEXT,
                        startDate TEXT NOT NULL,
                        endDate TEXT,
                        isActive INTEGER NOT NULL,
                        createdAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_medications_accountId ON medications(accountId)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS medication_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        medicationId TEXT NOT NULL,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        status TEXT NOT NULL,
                        recordedAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_medication_logs_medicationId_date_time ON medication_logs(medicationId, date, time)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_medication_logs_date ON medication_logs(date)")
            }
        }
    }
}
