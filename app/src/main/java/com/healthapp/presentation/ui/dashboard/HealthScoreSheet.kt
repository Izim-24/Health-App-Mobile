package com.healthapp.presentation.ui.dashboard

import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthapp.domain.usecase.HealthScore
import com.healthapp.domain.usecase.HealthScoreResult
import com.healthapp.domain.usecase.ScoreArea
import com.healthapp.domain.usecase.ScoreComponent
import com.healthapp.domain.usecase.ScoreInsight
import com.healthapp.domain.usecase.WeekScore
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import java.time.format.DateTimeFormatter
import com.healthapp.presentation.ui.theme.HealthPalette

private fun ScoreArea.route() = when (this) {
    ScoreArea.SLEEP, ScoreArea.ACTIVITY, ScoreArea.BLOOD_PRESSURE -> "health"
    ScoreArea.BMI -> "profile"
    ScoreArea.MEDICATION -> "medications"
    ScoreArea.HABITS -> "habits"
}

@Composable
private fun ScoreArea.howToAdd() = when (this) {
    ScoreArea.SLEEP -> stringResource(R.string.how_add_sleep)
    ScoreArea.ACTIVITY -> stringResource(R.string.how_add_activity)
    ScoreArea.BLOOD_PRESSURE -> stringResource(R.string.how_add_bp)
    ScoreArea.BMI -> stringResource(R.string.how_add_bmi)
    ScoreArea.MEDICATION, ScoreArea.HABITS -> ""
}

@Composable
private fun scoreColor(score: Int) = when {
    score >= 85 -> HealthPalette.Emerald.strong
    score >= 70 -> HealthPalette.Sky.strong
    score >= 50 -> HealthPalette.Amber.strong
    else -> HealthPalette.Coral.strong
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScoreSheet(
    result: HealthScoreResult?,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    history: List<WeekScore> = emptyList(),
    insights: List<ScoreInsight> = emptyList()
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HealthPalette.Background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.health_score), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
            if (result?.score != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${result.score}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = scoreColor(result.score))
                    Text(stringResource(R.string.score_of_100_grade, result.grade!!.title()), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Muted, modifier = Modifier.padding(bottom = 6.dp))
                }
            } else {
                Text(
                    result?.let { stringResource(R.string.score_min_areas_have, HealthScore.MIN_AREAS, it.components.size) } ?: stringResource(R.string.score_min_areas, HealthScore.MIN_AREAS),
                    color = HealthPalette.Muted
                )
            }

            // Start the chart at the first week with a score (keep at least 4 bars so it still reads as a trend).
            val shownWeeks = history.dropWhile { it.score == null }.let { if (it.size < 4) history.takeLast(4) else it }
            if (history.count { it.score != null } >= 2) ScoreHistoryCard(shownWeeks)
            if (insights.isNotEmpty()) InsightsCard(insights)

            result?.components?.forEach { ComponentRow(it, onClick = { onOpen(it.area.route()) }) }

            if (!result?.missing.isNullOrEmpty()) {
                Text(stringResource(R.string.no_data_yet), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
                result!!.missing.forEach { area ->
                    Card(onClick = { onOpen(area.route()) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Surface), border = androidx.compose.foundation.BorderStroke(1.dp, HealthPalette.Line)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(area.title(), fontWeight = FontWeight.SemiBold, color = HealthPalette.Muted)
                                Text(area.howToAdd(), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                            }
                            Text("›", color = HealthPalette.Muted, modifier = Modifier.clearAndSetSemantics { })
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.score_method, bmiNormalRange()),
                style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted
            )
        }
    }
}

@Composable
private fun ComponentRow(c: ScoreComponent, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Surface), border = androidx.compose.foundation.BorderStroke(1.dp, HealthPalette.Line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.area.title(), fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
                Text("${c.score}", fontWeight = FontWeight.Bold, color = scoreColor(c.score))
            }
            LinearProgressIndicator(
                progress = { c.score / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = scoreColor(c.score),
                trackColor = HealthPalette.SurfaceAlt
            )
            Text(c.detail.text(), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            c.tip?.let { Text(it.text(), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink) }
        }
    }
}

private val weekLabel = DateTimeFormatter.ofPattern("d/M")

/** Bar per rolling 7-day window (label = last day of the window); empty weeks show a faint stub. */
@Composable
private fun ScoreHistoryCard(history: List<WeekScore>) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Surface), border = BorderStroke(1.dp, HealthPalette.Line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.weeks_recent, history.size), fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
            Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                history.forEachIndexed { i, week ->
                    val score = week.score
                    val label = if (i == history.lastIndex) stringResource(R.string.week_now) else week.weekEnd.format(weekLabel)
                    val barDescription = stringResource(
                        R.string.week_bar_cd, label,
                        score?.let { stringResource(R.string.points_value, it) } ?: stringResource(R.string.not_enough_data)
                    )
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = barDescription },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(score?.toString() ?: "–", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (score != null) scoreColor(score) else HealthPalette.Subtle)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(((score ?: 4) / 100f).coerceIn(0.04f, 1f) * 0.72f)
                                .background(
                                    if (score != null) scoreColor(score).copy(alpha = if (i == history.lastIndex) 1f else 0.55f) else HealthPalette.SurfaceAlt,
                                    RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = HealthPalette.Muted, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
            Text(stringResource(R.string.weeks_explainer),
                style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
        }
    }
}

@Composable
private fun InsightsCard(insights: List<ScoreInsight>) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Surface), border = BorderStroke(1.dp, HealthPalette.Line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.insights_title), fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
            insights.forEach { insight ->
                val (symbol, color) = when (insight.trend) {
                    ScoreInsight.Trend.UP -> "▲" to HealthPalette.Emerald.strong
                    ScoreInsight.Trend.DOWN -> "▼" to HealthPalette.Coral.strong
                    ScoreInsight.Trend.INFO -> "•" to HealthPalette.Indigo.strong
                }
                val trendLabel = when (insight.trend) {
                    ScoreInsight.Trend.UP -> stringResource(R.string.trend_up)
                    ScoreInsight.Trend.DOWN -> stringResource(R.string.trend_down)
                    ScoreInsight.Trend.INFO -> null
                }
                Row(Modifier.semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // TalkBack says "Up"/"Down" instead of the triangle's character name; the bullet is skipped.
                    Text(symbol, color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.width(16.dp).clearAndSetSemantics { trendLabel?.let { contentDescription = it } })
                    Text(insight.text(), style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Ink)
                }
            }
        }
    }
}
