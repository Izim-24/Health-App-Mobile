package com.healthapp.presentation.ui.mental

import com.healthapp.presentation.ui.theme.Illustration
import com.healthapp.presentation.ui.theme.IllustrationImage
import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.annotation.StringRes
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthapp.presentation.ui.theme.ArtworkKind
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.components.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.theme.WellnessArtwork
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthapp.presentation.viewmodel.MentalHealthViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [label] is the value saved in the database (Vietnamese, kept for existing entries); [title] is
 * what the user sees in the current language.
 */
private data class MoodOption(val label: String, @StringRes val title: Int, val emoji: String, val accent: Accent)
private val moodOptions: List<MoodOption> @Composable get() = listOf(
    MoodOption("Vui", R.string.mood_happy, "😊", HealthPalette.Amber),
    MoodOption("Bình yên", R.string.mood_calm, "😌", HealthPalette.Emerald),
    MoodOption("Mệt", R.string.mood_tired, "😴", HealthPalette.Violet),
    MoodOption("Buồn", R.string.mood_sad, "😔", HealthPalette.Sky),
    MoodOption("Căng thẳng", R.string.mood_stressed, "😟", HealthPalette.Coral)
)

@Composable
fun MentalHealthScreen(viewModel: MentalHealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val entries = state.entries
    val existing = state.today
    // Form fields start from today's saved check-in once it has loaded from the database.
    var selectedMood by remember(existing) { mutableStateOf(existing?.mood.orEmpty()) }
    var stress by remember(existing) { mutableFloatStateOf(existing?.stress?.toFloat() ?: 3f) }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    val saved = state.savedToday
    var breathing by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(60) }

    LaunchedEffect(breathing) {
        while (breathing && secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        if (secondsLeft == 0) breathing = false
    }

    Column(Modifier.fillMaxSize().background(HealthPalette.Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader(stringResource(R.string.tab_mind), stringResource(R.string.mind_header), subtitle = stringResource(R.string.mind_header_sub), accent = HealthPalette.Pink)

        GradientCard(HealthPalette.Pink.gradient, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.checkin_today), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                    Text(stringResource(R.string.how_feel), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(stringResource(R.string.no_right_answer), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
                }
                WellnessArtwork(ArtworkKind.MIND, Modifier.size(96.dp))
            }
        }

        SectionHeader(stringResource(R.string.choose_mood), modifier = Modifier.padding(top = 14.dp))
        moodOptions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val selected = selectedMood == option.label
                    Card(
                        onClick = { selectedMood = option.label; viewModel.markDirty() },
                        modifier = Modifier.weight(1f).semantics { this.selected = selected; role = Role.RadioButton },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected) option.accent.soft else HealthPalette.Surface),
                        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) option.accent.strong else HealthPalette.Line)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.emoji, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
                            Text(stringResource(option.title), style = MaterialTheme.typography.labelMedium, color = if (selected) option.accent.strong else HealthPalette.Ink, maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(6.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                val stressAccent = when { stress >= 7 -> HealthPalette.Coral; stress >= 4 -> HealthPalette.Amber; else -> HealthPalette.Emerald }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stress_level), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
                    Pill("${stress.toInt()}/10", stressAccent)
                }
                val stressLabel = stringResource(R.string.stress_level)
                Slider(
                    modifier = Modifier.semantics { contentDescription = stressLabel },
                    value = stress, onValueChange = { stress = it; viewModel.markDirty() }, valueRange = 0f..10f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = stressAccent.strong, activeTrackColor = stressAccent.strong, inactiveTrackColor = stressAccent.soft, activeTickColor = Color.White.copy(alpha = .6f), inactiveTickColor = stressAccent.strong.copy(alpha = .3f))
                )
                Text(stringResource(R.string.stress_scale), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; viewModel.markDirty() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.private_note)) },
                    placeholder = { Text(stringResource(R.string.private_note_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    shape = MaterialTheme.shapes.medium
                )
                Text(stringResource(R.string.note_storage), style = MaterialTheme.typography.labelSmall, color = HealthPalette.Muted)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    stringResource(if (saved) R.string.saved_today else R.string.save_checkin),
                    onClick = {
                        viewModel.save(selectedMood, stress.toInt(), note)
                    },
                    enabled = selectedMood.isNotBlank(),
                    color = HealthPalette.Pink.strong
                )
                state.error?.let {
                    Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        GradientCard(HealthPalette.Sky.gradient, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.breathe_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(stringResource(R.string.breathe_sub), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
                }
                // Circle grows on inhale and shrinks on exhale
                val inhale = (60 - secondsLeft) % 8 < 4
                val scale by androidx.compose.animation.core.animateFloatAsState(if (breathing && inhale) 1f else 0.6f, androidx.compose.animation.core.tween(3800), label = "breath")
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(72.dp * scale).clip(CircleShape).background(Color.White.copy(alpha = .35f)))
                    Box(Modifier.size(40.dp * scale).clip(CircleShape).background(Color.White))
                }
            }
            Spacer(Modifier.height(14.dp))
            val phase = stringResource(if ((60 - secondsLeft) % 8 < 4) R.string.breathe_in else R.string.breathe_out)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (breathing) stringResource(R.string.breathe_phase, phase, secondsLeft) else stringResource(if (secondsLeft == 0) R.string.breathe_done else R.string.breathe_ready), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (!breathing && secondsLeft == 0) secondsLeft = 60
                        breathing = !breathing
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HealthPalette.Sky.strong)
                ) { Text(stringResource(if (breathing) R.string.action_pause else if (secondsLeft == 60) R.string.action_start else R.string.action_continue), style = MaterialTheme.typography.labelLarge) }
            }
        }

        SectionHeader(stringResource(R.string.recent_journal), modifier = Modifier.padding(top = 18.dp))
        if (entries.isEmpty()) {
            EmptyState(Illustration.JOURNAL, stringResource(R.string.journal_empty_title), stringResource(R.string.no_checkins))
        } else {
            entries.sortedByDescending { it.date }.take(7).forEach { entry ->
                val option = moodOptions.firstOrNull { it.label == entry.mood }
                AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(option?.accent?.soft ?: HealthPalette.SurfaceAlt), contentAlignment = Alignment.Center) {
                            Text(option?.emoji ?: "•", fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics { })
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.date_mood, formatDate(entry.date), option?.let { stringResource(it.title) } ?: entry.mood), style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                            Text(stringResource(R.string.stress_value, entry.stress) + if (entry.note.isNotBlank()) " · ${entry.note.take(55)}" else "", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, maxLines = 2)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.mind_disclaimer), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", currentLocale()))
