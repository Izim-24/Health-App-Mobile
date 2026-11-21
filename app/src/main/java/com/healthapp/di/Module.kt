package com.healthapp.di

import android.content.Context
import androidx.room.Room
import com.healthapp.data.local.HealthDatabase
import com.healthapp.data.remote.OpenFoodFactsApi
import com.healthapp.data.remote.AiBackendApi
import com.healthapp.BuildConfig
import com.healthapp.data.local.dao.*
import com.healthapp.data.repository.*
import com.healthapp.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideOpenFoodFactsApi(): OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl("https://world.openfoodfacts.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenFoodFactsApi::class.java)

    @Singleton
    @Provides
    fun provideAiBackendApi(): AiBackendApi = Retrofit.Builder()
        .baseUrl(if (BuildConfig.DEBUG) "http://10.0.2.2:8787/" else "https://example.invalid/")
        // Vision requests take longer than chat: allow up to a minute.
        .client(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AiBackendApi::class.java)

    @Singleton
    @Provides
    fun provideHealthDatabase(
        @ApplicationContext context: Context
    ): HealthDatabase {
        return Room.databaseBuilder(
            context,
            HealthDatabase::class.java,
            "health_database"
        ).addMigrations(*HealthDatabase.ALL_MIGRATIONS).build()
    }

    @Singleton
    @Provides
    fun provideAccountDao(db: HealthDatabase): AccountDao = db.accountDao()

    @Singleton
    @Provides
    fun provideUserProfileDao(db: HealthDatabase): UserProfileDao = db.userProfileDao()

    @Singleton
    @Provides
    fun provideHabitDao(db: HealthDatabase): HabitDao = db.habitDao()

    @Singleton
    @Provides
    fun provideHabitLogDao(db: HealthDatabase): HabitLogDao = db.habitLogDao()

    @Singleton
    @Provides
    fun provideFoodDao(db: HealthDatabase): FoodDao = db.foodDao()

    @Singleton
    @Provides
    fun provideMealDao(db: HealthDatabase): MealDao = db.mealDao()

    @Singleton
    @Provides
    fun provideMealItemDao(db: HealthDatabase): MealItemDao = db.mealItemDao()

    @Singleton
    @Provides
    fun provideWaterLogDao(db: HealthDatabase): WaterLogDao = db.waterLogDao()

    @Singleton
    @Provides
    fun provideSleepLogDao(db: HealthDatabase): SleepLogDao = db.sleepLogDao()

    @Singleton
    @Provides
    fun provideActivityLogDao(db: HealthDatabase): ActivityLogDao = db.activityLogDao()

    @Singleton
    @Provides
    fun provideWeightLogDao(db: HealthDatabase): WeightLogDao = db.weightLogDao()

    @Singleton
    @Provides
    fun provideVitalLogDao(db: HealthDatabase): VitalLogDao = db.vitalLogDao()

    @Singleton
    @Provides
    fun provideReminderDao(db: HealthDatabase): ReminderDao = db.reminderDao()


    @Singleton
    @Provides
    fun provideDataManagementDao(db: HealthDatabase): DataManagementDao = db.dataManagementDao()

    @Singleton
    @Provides
    fun provideMedicationDao(db: HealthDatabase): MedicationDao = db.medicationDao()

    @Singleton
    @Provides
    fun provideHealthConnectDao(db: HealthDatabase): HealthConnectDao = db.healthConnectDao()

    @Singleton
    @Provides
    fun provideMoodDao(db: HealthDatabase): MoodDao = db.moodDao()

    @Singleton
    @Provides
    fun provideDemoDataDao(db: HealthDatabase): DemoDataDao = db.demoDataDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Singleton
    @Provides
    fun provideNutritionRepository(
        foodDao: FoodDao,
        mealDao: MealDao,
        mealItemDao: MealItemDao,
        profileDao: UserProfileDao,
        openFoodFactsApi: OpenFoodFactsApi
    ): NutritionRepository = NutritionRepositoryImpl(foodDao, mealDao, mealItemDao, profileDao, openFoodFactsApi)

    @Singleton
    @Provides
    fun provideActivityRepository(
        activityLogDao: ActivityLogDao
    ): ActivityRepository = ActivityRepositoryImpl(activityLogDao)

    @Singleton
    @Provides
    fun provideHabitRepository(
        habitDao: HabitDao,
        habitLogDao: HabitLogDao
    ): HabitRepository = HabitRepositoryImpl(habitDao, habitLogDao)

    @Singleton
    @Provides
    fun provideAIRepository(impl: AIRepositoryImpl): AIRepository = impl

    @Singleton
    @Provides
    fun provideWeightRepository(
        weightLogDao: WeightLogDao
    ): WeightRepository = WeightRepositoryImpl(weightLogDao)

    @Singleton
    @Provides
    fun provideVitalRepository(dao: VitalLogDao): VitalRepository = VitalRepositoryImpl(dao)

    @Singleton
    @Provides
    fun provideSleepRepository(
        sleepLogDao: SleepLogDao
    ): SleepRepository = SleepRepositoryImpl(sleepLogDao)

    @Singleton
    @Provides
    fun provideWaterRepository(impl: WaterRepositoryImpl): WaterRepository = impl

    @Singleton
    @Provides
    fun provideReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository = impl

    @Provides fun provideAccountSession(impl: AccountManager): AccountSession = impl
    @Provides fun provideProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository = impl
    @Provides fun provideReportRepository(impl: ReportRepositoryImpl): ReportRepository = impl
    @Provides fun provideMedicationRepository(impl: MedicationRepositoryImpl): MedicationRepository = impl
    @Provides fun provideMoodRepository(impl: MoodRepositoryImpl): MoodRepository = impl
    @Provides fun provideDemoDataRepository(impl: DemoDataRepositoryImpl): DemoDataRepository = impl
    @Provides fun provideAppSettingsRepository(impl: AppSettingsRepositoryImpl): AppSettingsRepository = impl
    @Provides fun provideDataManagementRepository(impl: DataManagementRepositoryImpl): DataManagementRepository = impl
    @Provides fun provideHealthSyncRepository(impl: com.healthapp.data.healthconnect.HealthConnectManager): HealthSyncRepository = impl
}
