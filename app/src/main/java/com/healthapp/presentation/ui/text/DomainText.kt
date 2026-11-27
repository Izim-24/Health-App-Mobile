package com.healthapp.presentation.ui.text

import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.healthapp.R
import com.healthapp.domain.usecase.BmiCategory
import com.healthapp.domain.usecase.BpCategory
import com.healthapp.domain.usecase.HealthCalculations
import com.healthapp.domain.usecase.ScoreArea
import com.healthapp.domain.usecase.ScoreDetail
import com.healthapp.domain.usecase.ScoreGrade
import com.healthapp.domain.usecase.ScoreInsight
import com.healthapp.domain.usecase.ScoreTip
import kotlin.math.abs

/**
 * Text for a ViewModel to show without holding a Context: a string resource (localized when shown)
 * or text that is already final, such as an error message from the data layer.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
    /** A plural resource: [count] picks the form (e.g. "1 record" / "2 records"). */
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> text
    is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
}

/** The app's current language (follows the in-app choice), for date formatting. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Locale-aware number formatting (Vietnamese: 7,5 and 8.000; English: 7.5 and 8,000). */
fun oneDecimal(x: Double) = "%.1f".format(x)
fun grouped(n: Int) = "%,d".format(n)

@StringRes
fun ScoreArea.titleRes(): Int = when (this) {
    ScoreArea.SLEEP -> R.string.area_sleep
    ScoreArea.ACTIVITY -> R.string.area_activity
    ScoreArea.BLOOD_PRESSURE -> R.string.area_blood_pressure
    ScoreArea.BMI -> R.string.area_bmi
    ScoreArea.MEDICATION -> R.string.area_medication
    ScoreArea.HABITS -> R.string.area_habits
}

@Composable fun ScoreArea.title() = stringResource(titleRes())

@Composable
fun ScoreGrade.title() = stringResource(
    when (this) {
        ScoreGrade.EXCELLENT -> R.string.grade_excellent
        ScoreGrade.GOOD -> R.string.grade_good
        ScoreGrade.FAIR -> R.string.grade_fair
        ScoreGrade.POOR -> R.string.grade_poor
    }
)

@StringRes
fun BmiCategory.titleRes(): Int = when (this) {
    BmiCategory.SEVERELY_UNDERWEIGHT -> R.string.bmi_severely_underweight
    BmiCategory.UNDERWEIGHT -> R.string.bmi_underweight
    BmiCategory.NORMAL -> R.string.bmi_normal
    BmiCategory.OVERWEIGHT -> R.string.bmi_overweight
    BmiCategory.OBESE_1 -> R.string.bmi_obese_1
    BmiCategory.OBESE_2 -> R.string.bmi_obese_2
}

@Composable fun BmiCategory.title() = stringResource(titleRes())

/** "18,5–22,9" / "18.5–22.9" */
fun bmiNormalRange() = "${oneDecimal(HealthCalculations.BMI_NORMAL_MIN)}–${oneDecimal(HealthCalculations.BMI_NORMAL_MAX)}"

@Composable
fun BpCategory.title() = stringResource(
    when (this) {
        BpCategory.NORMAL -> R.string.bp_normal
        BpCategory.ELEVATED -> R.string.bp_elevated
        BpCategory.STAGE_1 -> R.string.bp_stage_1
        BpCategory.STAGE_2 -> R.string.bp_stage_2
        BpCategory.CRISIS -> R.string.bp_crisis
    }
)

@Composable
fun ScoreDetail.text(): String = when (this) {
    is ScoreDetail.Sleep -> pluralStringResource(R.plurals.detail_sleep, nights, oneDecimal(avgHours), nights)
    is ScoreDetail.Activity -> pluralStringResource(R.plurals.detail_activity, days, grouped(avgSteps), days)
    is ScoreDetail.BloodPressure -> stringResource(R.string.detail_bp, systolic, diastolic, category.title())
    is ScoreDetail.Bmi -> stringResource(R.string.detail_bmi, oneDecimal(bmi), category.title())
    is ScoreDetail.Medication -> pluralStringResource(R.plurals.detail_medication, due, taken, due)
    is ScoreDetail.Habits -> pluralStringResource(R.plurals.detail_habits, possible, done, possible)
}

@Composable
fun ScoreTip.text(): String = when (this) {
    ScoreTip.SLEEP_MORE -> stringResource(R.string.tip_sleep_more)
    ScoreTip.SLEEP_LESS -> stringResource(R.string.tip_sleep_less)
    ScoreTip.MORE_STEPS -> stringResource(R.string.tip_more_steps)
    ScoreTip.BP_SEE_DOCTOR -> stringResource(R.string.tip_bp_doctor)
    ScoreTip.BP_LIFESTYLE -> stringResource(R.string.tip_bp_lifestyle)
    ScoreTip.BMI_RANGE -> stringResource(R.string.tip_bmi_range, bmiNormalRange())
    ScoreTip.MED_REMINDERS -> stringResource(R.string.tip_med_reminders)
    ScoreTip.FEWER_HABITS -> stringResource(R.string.tip_fewer_habits)
}

@Composable
fun ScoreInsight.text(): String = when (this) {
    is ScoreInsight.ScoreChange -> when {
        diff >= 3 -> stringResource(R.string.insight_score_up, diff)
        diff <= -3 -> stringResource(R.string.insight_score_down, -diff)
        else -> stringResource(R.string.insight_score_stable)
    }
    is ScoreInsight.SleepChange -> stringResource(
        if (deltaHours > 0) R.string.insight_sleep_more else R.string.insight_sleep_less,
        oneDecimal(abs(deltaHours)), oneDecimal(nowHours)
    )
    is ScoreInsight.StepsChange -> stringResource(
        if (deltaSteps > 0) R.string.insight_steps_more else R.string.insight_steps_less, grouped(abs(deltaSteps))
    )
    is ScoreInsight.MedsChange -> stringResource(
        if (nowPct > beforePct) R.string.insight_meds_better else R.string.insight_meds_worse, nowPct, beforePct
    )
    is ScoreInsight.HabitsChange -> stringResource(R.string.insight_habits, nowPct, beforePct)
    is ScoreInsight.Focus -> stringResource(R.string.insight_focus, best.title(), bestScore, worst.title(), worstScore)
}

/** "Sáng" / "Breakfast" (chips). */
@StringRes
fun com.healthapp.domain.model.MealType.shortRes(): Int = when (this) {
    com.healthapp.domain.model.MealType.BREAKFAST -> R.string.meal_breakfast_short
    com.healthapp.domain.model.MealType.LUNCH -> R.string.meal_lunch_short
    com.healthapp.domain.model.MealType.DINNER -> R.string.meal_dinner_short
    com.healthapp.domain.model.MealType.SNACK -> R.string.meal_snack_short
}

/** "Bữa sáng" / "Breakfast" (headings). */
@StringRes
fun com.healthapp.domain.model.MealType.longRes(): Int = when (this) {
    com.healthapp.domain.model.MealType.BREAKFAST -> R.string.meal_breakfast
    com.healthapp.domain.model.MealType.LUNCH -> R.string.meal_lunch
    com.healthapp.domain.model.MealType.DINNER -> R.string.meal_dinner
    com.healthapp.domain.model.MealType.SNACK -> R.string.meal_snack
}

/** Short weekday names: "T2…CN" / "Mon…Sun". Read once in composition, then used in plain lambdas. */
@Composable
fun dayNames(): Map<com.healthapp.domain.model.DayOfWeek, String> = mapOf(
    com.healthapp.domain.model.DayOfWeek.MONDAY to stringResource(R.string.day_mon),
    com.healthapp.domain.model.DayOfWeek.TUESDAY to stringResource(R.string.day_tue),
    com.healthapp.domain.model.DayOfWeek.WEDNESDAY to stringResource(R.string.day_wed),
    com.healthapp.domain.model.DayOfWeek.THURSDAY to stringResource(R.string.day_thu),
    com.healthapp.domain.model.DayOfWeek.FRIDAY to stringResource(R.string.day_fri),
    com.healthapp.domain.model.DayOfWeek.SATURDAY to stringResource(R.string.day_sat),
    com.healthapp.domain.model.DayOfWeek.SUNDAY to stringResource(R.string.day_sun)
)

// ---- Health alerts (VitalAlerts) ----

fun com.healthapp.domain.usecase.AlertKind.isBloodPressure(): Boolean = this == com.healthapp.domain.usecase.AlertKind.BP_CRISIS ||
    this == com.healthapp.domain.usecase.AlertKind.BP_HIGH || this == com.healthapp.domain.usecase.AlertKind.BP_SUSTAINED

@StringRes
fun com.healthapp.domain.usecase.AlertKind.titleRes(): Int = when (this) {
    com.healthapp.domain.usecase.AlertKind.REST_TACHY -> R.string.alert_title_rest_tachy
    com.healthapp.domain.usecase.AlertKind.REST_TACHY_SEVERE -> R.string.alert_title_rest_tachy_severe
    com.healthapp.domain.usecase.AlertKind.REST_BRADY -> R.string.alert_title_rest_brady
    com.healthapp.domain.usecase.AlertKind.OVER_EXERTION -> R.string.alert_title_over_exertion
    com.healthapp.domain.usecase.AlertKind.ABOVE_MAX_HR -> R.string.alert_title_above_max_hr
    com.healthapp.domain.usecase.AlertKind.BP_CRISIS -> R.string.alert_title_bp_crisis
    com.healthapp.domain.usecase.AlertKind.BP_HIGH -> R.string.alert_title_bp_high
    com.healthapp.domain.usecase.AlertKind.BP_SUSTAINED -> R.string.alert_title_bp_sustained
}

/** Explanation + what to do; takes (value, secondary). Same texts as the notification. */
@StringRes
fun com.healthapp.domain.usecase.AlertKind.bodyRes(): Int = when (this) {
    com.healthapp.domain.usecase.AlertKind.REST_TACHY -> R.string.alert_body_rest_tachy
    com.healthapp.domain.usecase.AlertKind.REST_TACHY_SEVERE -> R.string.alert_body_rest_tachy_severe
    com.healthapp.domain.usecase.AlertKind.REST_BRADY -> R.string.alert_body_rest_brady
    com.healthapp.domain.usecase.AlertKind.OVER_EXERTION -> R.string.alert_body_over_exertion
    com.healthapp.domain.usecase.AlertKind.ABOVE_MAX_HR -> R.string.alert_body_above_max_hr
    com.healthapp.domain.usecase.AlertKind.BP_CRISIS -> R.string.alert_body_bp_crisis
    com.healthapp.domain.usecase.AlertKind.BP_HIGH -> R.string.alert_body_bp_high
    com.healthapp.domain.usecase.AlertKind.BP_SUSTAINED -> R.string.alert_body_bp_sustained
}

@StringRes
fun com.healthapp.domain.usecase.ActivityState.labelRes(): Int = when (this) {
    com.healthapp.domain.usecase.ActivityState.RESTING -> R.string.alert_state_resting
    com.healthapp.domain.usecase.ActivityState.ACTIVE -> R.string.alert_state_active
    com.healthapp.domain.usecase.ActivityState.RECOVERING -> R.string.alert_state_recovering
}

/** "126 bpm" or "186/118 mmHg". */
@Composable
fun alertValue(alert: com.healthapp.domain.usecase.VitalAlert): String =
    if (alert.kind.isBloodPressure()) stringResource(R.string.alert_value_bp, alert.value, alert.secondary ?: 0)
    else stringResource(R.string.bpm_value, alert.value)

