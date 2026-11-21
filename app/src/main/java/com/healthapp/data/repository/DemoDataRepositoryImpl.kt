package com.healthapp.data.repository

import com.healthapp.data.local.dao.DemoDataDao
import com.healthapp.data.local.entity.*
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.DemoDataRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Fills the last 14 days with realistic sample data for demos. Every row id starts with "demo_",
 * so [clear] removes exactly the sample data and never touches the user's own entries.
 * A fixed random seed makes every demo look the same.
 */
class DemoDataRepositoryImpl @Inject constructor(
    private val dao: DemoDataDao,
    private val session: AccountSession
) : DemoDataRepository {

    private data class Dish(val key: String, val name: String, val grams: Int, val kcal: Int, val p: Double, val c: Double, val f: Double)

    private val dishes = listOf(
        Dish("pho", "Phở bò", 500, 450, 28.0, 55.0, 12.0),
        Dish("banhmi", "Bánh mì thịt", 200, 420, 18.0, 50.0, 15.0),
        Dish("oat", "Cháo yến mạch", 250, 180, 6.0, 30.0, 4.0),
        Dish("egg", "Trứng luộc", 50, 78, 6.3, 0.6, 5.3),
        Dish("comtam", "Cơm tấm sườn", 400, 650, 32.0, 80.0, 22.0),
        Dish("buncha", "Bún chả", 450, 550, 25.0, 60.0, 22.0),
        Dish("comga", "Cơm gà", 400, 580, 30.0, 70.0, 18.0),
        Dish("rice", "Cơm trắng", 200, 260, 5.0, 57.0, 0.6),
        Dish("cakho", "Cá kho tộ", 150, 220, 25.0, 5.0, 11.0),
        Dish("raumuong", "Rau muống xào tỏi", 150, 110, 4.0, 7.0, 8.0),
        Dish("banana", "Chuối", 120, 105, 1.3, 27.0, 0.4),
        Dish("yogurt", "Sữa chua", 100, 80, 4.0, 12.0, 2.0)
    ).associateBy { it.key }

    override suspend fun hasDemoData(): Boolean = dao.demoMealCount() > 0

    override suspend fun seed(): Result<Int> = runCatching {
        val accountId = session.getCurrentAccountId()
        dao.clearAll() // re-seeding replaces old sample data instead of duplicating it
        val rnd = Random(2026)
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val days = (13 downTo 0).map { today.minusDays(it.toLong()) }

        val foods = dishes.values.map { d ->
            FoodEntity("demo_food_${d.key}", d.name, d.kcal, d.p, d.c, d.f, 0.0, 0.0, 0, d.grams, "DEMO")
        }
        val meals = mutableListOf<MealEntity>(); val items = mutableListOf<MealItemEntity>()
        fun meal(date: LocalDate, type: String, hour: Int, keys: List<String>) {
            val id = "demo_meal_${date}_$type"
            val chosen = keys.map { dishes.getValue(it) }
            chosen.forEachIndexed { i, d ->
                items += MealItemEntity("demo_item_${date}_${type}_$i", id, "demo_food_${d.key}", 1.0, d.grams, d.kcal, d.p, d.c, d.f, 0.0, 0.0, 0)
            }
            val at = date.atTime(hour, rnd.nextInt(0, 50))
            meals += MealEntity(id, accountId, type, date.toString(), chosen.sumOf { it.kcal }, chosen.sumOf { it.p }, chosen.sumOf { it.c },
                chosen.sumOf { it.f }, 0.0, 0.0, 0, "Dữ liệu mẫu", null, at, at)
        }

        val water = mutableListOf<WaterLogEntity>(); val sleep = mutableListOf<SleepLogEntity>(); val activity = mutableListOf<ActivityLogEntity>()
        val weight = mutableListOf<WeightLogEntity>(); val vitals = mutableListOf<VitalLogEntity>(); val moods = mutableListOf<MoodEntity>()
        val habitDefs = listOf(
            Triple("demo_habit_water", "Uống đủ 2 lít nước", 0.8),
            Triple("demo_habit_walk", "Đi bộ 30 phút", 0.7),
            Triple("demo_habit_sleep", "Ngủ trước 23:30", 0.6)
        )
        val habitLogs = mutableListOf<HabitLogEntity>(); val medLogs = mutableListOf<MedicationLogEntity>()
        val moodsList = listOf("Vui", "Bình yên", "Mệt", "Bình yên", "Vui", "Căng thẳng", "Buồn")

        days.forEachIndexed { i, date ->
            val isToday = date == today
            // Meals: today only up to lunch, so the dashboard shows a day in progress.
            meal(date, "BREAKFAST", 7, listOf(listOf("pho"), listOf("banhmi"), listOf("oat", "egg"))[i % 3])
            if (!isToday || now.hour >= 12) meal(date, "LUNCH", 12, listOf(listOf("comtam"), listOf("buncha"), listOf("comga"))[i % 3])
            if (!isToday) {
                meal(date, "DINNER", 19, listOf("rice", "cakho", "raumuong"))
                if (i % 2 == 0) meal(date, "SNACK", 16, listOf(listOf("banana"), listOf("yogurt"))[i % 4 / 2])
            }
            // Water: 6–9 glasses on past days, 3 so far today.
            val glasses = if (isToday) 3 else rnd.nextInt(6, 10)
            repeat(glasses) { g -> water += WaterLogEntity("demo_water_${date}_$g", accountId, 250, date.atTime(7 + g * 2, 15), date.toString()) }
            // Sleep: the night ending on this date.
            val bed = date.minusDays(1).atTime(22, 0).plusMinutes(rnd.nextLong(15, 150))
            val minutes = rnd.nextInt(370, 500)
            sleep += SleepLogEntity("demo_sleep_$date", accountId, bed, bed.plusMinutes(minutes.toLong()), minutes, rnd.nextInt(5, 10), "Dữ liệu mẫu", date.toString())
            // Activity
            val steps = if (isToday) 3800 else rnd.nextInt(4500, 10500)
            activity += ActivityLogEntity("demo_steps_$date", accountId, "WALKING", steps / 110, steps, (steps * 0.00075 * 10).roundToInt() / 10.0,
                (steps * 0.04).roundToInt(), "MODERATE", if (isToday) now else date.atTime(21, 0), date.toString())
            // Weight every other day, slowly trending down.
            if (i % 2 == 0) weight += WeightLogEntity("demo_weight_$date", accountId, ((63.4 - i * 0.08 + rnd.nextDouble(-0.3, 0.3)) * 10).roundToInt() / 10.0, date.atTime(6, 45), date.toString())
            // Vitals: resting heart rate daily, blood pressure every 2 days, glucose twice a week.
            vitals += VitalLogEntity("demo_hr_$date", accountId, "HEART_RATE", rnd.nextInt(62, 76).toDouble(), null, "bpm", "DEMO", date.atTime(7, 0))
            if (i % 2 == 1) vitals += VitalLogEntity("demo_bp_$date", accountId, "BLOOD_PRESSURE", rnd.nextInt(114, 129).toDouble(), rnd.nextInt(72, 83).toDouble(), "mmHg", "DEMO", date.atTime(7, 5))
            if (i % 4 == 0) vitals += VitalLogEntity("demo_glu_$date", accountId, "GLUCOSE", (rnd.nextDouble(4.9, 5.9) * 10).roundToInt() / 10.0, null, "mmol/L", "DEMO", date.atTime(6, 50))
            // Habits
            habitDefs.forEachIndexed { h, (id, _, rate) ->
                val done = if (isToday) h == 0 else rnd.nextDouble() < rate
                if (done) habitLogs += HabitLogEntity("demo_hlog_${id}_$date", id, date.toString(), true, date.atTime(20, h))
            }
            // Medication: Vitamin D3 at 08:00, ~90% taken; today only if 08:00 has passed.
            if (!isToday || now.toLocalTime() > LocalTime.of(8, 0)) {
                val status = if (rnd.nextDouble() < 0.9) "TAKEN" else "SKIPPED"
                medLogs += MedicationLogEntity("demo_medlog_$date", "demo_med_vitd", date.toString(), "08:00", status, date.atTime(8, 5))
            }
            // Mood: past days only, so today's check-in stays for the live demo.
            if (!isToday) moods += MoodEntity("demo_mood_$date", accountId, date.toString(), moodsList[i % moodsList.size], rnd.nextInt(2, 8), "Dữ liệu mẫu", date.atTime(21, 30))
        }

        val created = days.first().atStartOfDay().minusDays(1)
        val habits = habitDefs.map { (id, name, _) -> HabitEntity(id, accountId, name, "Dữ liệu mẫu", "DAILY", null, null, null, null, true, created, created) }
        val meds = listOf(MedicationEntity("demo_med_vitd", accountId, "Vitamin D3", "1000 IU", "Sau bữa sáng", "08:00", null, days.first().toString(), null, true, created))

        // Children before parents: list screens observe the parent tables (see NutritionRepositoryImpl.addMeal).
        dao.foods(foods); dao.mealItems(items); dao.meals(meals)
        dao.water(water); dao.sleep(sleep); dao.activity(activity); dao.weight(weight); dao.vitals(vitals)
        dao.habitLogs(habitLogs); dao.habits(habits)
        dao.medicationLogs(medLogs); dao.medications(meds)
        dao.moods(moods)
        days.size
    }

    override suspend fun clear(): Result<Unit> = runCatching { dao.clearAll() }
}
