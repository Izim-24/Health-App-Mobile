package com.healthapp.domain.usecase

import kotlin.math.roundToInt

enum class ScoreArea(val label: String, val weight: Int) {
    SLEEP("Giấc ngủ", 20),
    ACTIVITY("Vận động", 20),
    BLOOD_PRESSURE("Huyết áp", 20),
    BMI("BMI", 15),
    MEDICATION("Uống thuốc đúng giờ", 15),
    HABITS("Thói quen", 10)
}

data class ScoreComponent(
    val area: ScoreArea,
    val score: Int,        // 0..100
    val detail: String,    // what the score is based on
    val tip: String?       // shown when there is room to improve
)

data class HealthScoreResult(
    val score: Int?,                       // null until enough areas have data
    val components: List<ScoreComponent>,
    val missing: List<ScoreArea>
) {
    val grade: String? get() = score?.let {
        when {
            it >= 85 -> "Rất tốt"
            it >= 70 -> "Tốt"
            it >= 50 -> "Cần cải thiện"
            else -> "Cần chú ý"
        }
    }
    /** The weakest area with a tip, to surface on the dashboard. */
    val topTip: ScoreComponent? get() = components.filter { it.tip != null }.minByOrNull { it.score }
}

/** Inputs, each already summarised over its window. Null = no data for that area. */
data class HealthScoreInput(
    val avgSleepMinutes: Double? = null, val sleepNights: Int = 0,             // last 7 nights
    val avgDailySteps: Double? = null, val stepDays: Int = 0,                   // recent full days
    val bmi: Double? = null,
    val systolic: Int? = null, val diastolic: Int? = null,                     // latest within 30 days
    val habitCompletedDays: Int = 0, val habitPossibleDays: Int = 0,            // summed over active habits
    val medsTaken: Int = 0, val medsDue: Int = 0                                // last 7 days
)

/**
 * A transparent wellness score: each area is scored 0–100 against a public guideline, then
 * averaged by weight over the areas that have data. Not a medical assessment or diagnosis.
 *
 *  - Sleep: 7–9 h/night for adults (AASM / National Sleep Foundation).
 *  - Activity: ~8,000 steps/day, where mortality benefit largely plateaus for adults
 *    (Paluch et al., Lancet Public Health 2022); 2,000 steps/day or less scores 0.
 *  - Blood pressure: ACC/AHA 2017 categories; the worse of systolic/diastolic counts.
 *  - BMI: WHO Asia-Pacific categories (normal 18.5–22.9), see [BmiCategory].
 *  - Medication: share of due doses marked taken (≥80% is the usual "adherent" threshold).
 *  - Habits: share of possible habit-days completed.
 */
object HealthScore {
    const val MIN_AREAS = 3

    fun sleepScore(avgMinutes: Double): Int {
        val h = avgMinutes / 60.0
        val s = when {
            h < 7 -> 100 - (7 - h) * 25      // 6h → 75, 5h → 50, 3h → 0
            h <= 9 -> 100.0
            else -> 100 - (h - 9) * 20       // 10h → 80
        }
        return s.roundToInt().coerceIn(0, 100)
    }

    fun stepsScore(avgSteps: Double): Int = ((avgSteps - 2000) / (8000 - 2000) * 100).roundToInt().coerceIn(0, 100)

    /** ACC/AHA category score and Vietnamese label. */
    fun bloodPressure(sys: Int, dia: Int): Pair<Int, String> = when {
        sys > 180 || dia > 120 -> 10 to "rất cao — cần đi khám ngay"
        sys >= 140 || dia >= 90 -> 35 to "tăng huyết áp độ 2"
        sys >= 130 || dia >= 80 -> 60 to "tăng huyết áp độ 1"
        sys >= 120 -> 80 to "hơi cao"
        else -> 100 to "bình thường"
    }

    fun bmiScore(bmi: Double): Pair<Int, String> =
        HealthCalculations.bmiCategory(bmi).let { it.score to it.label }

    fun calculate(input: HealthScoreInput): HealthScoreResult {
        val parts = mutableListOf<ScoreComponent>()

        input.avgSleepMinutes?.takeIf { input.sleepNights > 0 }?.let { m ->
            val s = sleepScore(m)
            val h = m / 60
            parts += ScoreComponent(ScoreArea.SLEEP, s,
                "Trung bình ${"%.1f".format(h)} giờ/đêm (${input.sleepNights} đêm gần nhất)",
                when { h < 7 -> "Ngủ thêm để đạt 7–9 giờ mỗi đêm."; h > 9 -> "Ngủ quá 9 giờ thường xuyên — cân nhắc hỏi bác sĩ nếu luôn mệt."; else -> null })
        }
        input.avgDailySteps?.takeIf { input.stepDays > 0 }?.let { avg ->
            val s = stepsScore(avg)
            parts += ScoreComponent(ScoreArea.ACTIVITY, s,
                "Trung bình ${avg.roundToInt()} bước/ngày (${input.stepDays} ngày)",
                if (s < 100) "Đặt mục tiêu khoảng 8.000 bước/ngày; thêm 1.000 bước mỗi tuần." else null)
        }
        if (input.systolic != null && input.diastolic != null) {
            val (s, label) = bloodPressure(input.systolic, input.diastolic)
            parts += ScoreComponent(ScoreArea.BLOOD_PRESSURE, s,
                "${input.systolic}/${input.diastolic} mmHg — $label",
                when { s <= 35 -> "Huyết áp cao: nên trao đổi với bác sĩ."; s < 100 -> "Giảm muối, vận động đều và đo lại vào ngày khác."; else -> null })
        }
        input.bmi?.takeIf { it > 0 }?.let { bmi ->
            val (s, label) = bmiScore(bmi)
            parts += ScoreComponent(ScoreArea.BMI, s, "BMI ${"%.1f".format(bmi)} — $label",
                if (s < 100) "BMI khuyến nghị cho người châu Á là ${HealthCalculations.BMI_NORMAL_RANGE}; tham khảo chuyên gia dinh dưỡng." else null)
        }
        if (input.medsDue > 0) {
            val rate = input.medsTaken.toDouble() / input.medsDue
            parts += ScoreComponent(ScoreArea.MEDICATION, (rate * 100).roundToInt().coerceIn(0, 100),
                "${input.medsTaken}/${input.medsDue} liều đã uống trong 7 ngày",
                if (rate < 0.8) "Bật nhắc nhở và đánh dấu liều ngay khi uống." else null)
        }
        if (input.habitPossibleDays > 0) {
            val rate = input.habitCompletedDays.toDouble() / input.habitPossibleDays
            parts += ScoreComponent(ScoreArea.HABITS, (rate * 100).roundToInt().coerceIn(0, 100),
                "Hoàn thành ${input.habitCompletedDays}/${input.habitPossibleDays} lượt thói quen trong 7 ngày",
                if (rate < 0.7) "Bớt số thói quen hoặc đặt nhắc nhở để duy trì đều hơn." else null)
        }

        val present = parts.map { it.area }.toSet()
        // Medication and habits only count if the user uses those features, so they're never "missing".
        val missing = listOf(ScoreArea.SLEEP, ScoreArea.ACTIVITY, ScoreArea.BLOOD_PRESSURE, ScoreArea.BMI).filter { it !in present }
        val score = if (parts.size >= MIN_AREAS) {
            val totalWeight = parts.sumOf { it.area.weight }
            (parts.sumOf { it.score * it.area.weight }.toDouble() / totalWeight).roundToInt()
        } else null
        return HealthScoreResult(score, parts.sortedBy { it.area.ordinal }, missing)
    }
}
