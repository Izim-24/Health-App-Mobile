package com.healthapp.presentation.ui.mental

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import com.healthapp.presentation.ui.theme.ArtworkKind
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.components.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.theme.WellnessArtwork
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class MoodOption(val label: String, val emoji: String, val accent: Accent)
private val moodOptions = listOf(
    MoodOption("Vui", "😊", HealthPalette.Amber),
    MoodOption("Bình yên", "😌", HealthPalette.Emerald),
    MoodOption("Mệt", "😴", HealthPalette.Violet),
    MoodOption("Buồn", "😔", HealthPalette.Sky),
    MoodOption("Căng thẳng", "😟", HealthPalette.Coral)
)

private data class MoodEntry(val date: String, val mood: String, val stress: Int, val note: String)

@Composable
fun MentalHealthScreen() {
    val context = LocalContext.current
    val today = LocalDate.now().toString()
    var entries by remember { mutableStateOf(readEntries(context)) }
    val existing = entries.firstOrNull { it.date == today }
    var selectedMood by remember(today) { mutableStateOf(existing?.mood.orEmpty()) }
    var stress by remember(today) { mutableFloatStateOf(existing?.stress?.toFloat() ?: 3f) }
    var note by remember(today) { mutableStateOf(existing?.note.orEmpty()) }
    var saved by remember { mutableStateOf(false) }
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
        ScreenHeader("Tinh thần", "Lắng nghe bản thân", "Một khoảng dừng nhỏ cho ngày hôm nay.", HealthPalette.Pink)

        GradientCard(HealthPalette.Pink.gradient, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Check-in hôm nay", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                    Text("Bạn đang cảm thấy thế nào?", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Không có câu trả lời đúng hay sai.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
                }
                WellnessArtwork(ArtworkKind.MIND, Modifier.size(96.dp))
            }
        }

        SectionHeader("Chọn cảm xúc", modifier = Modifier.padding(top = 14.dp))
        moodOptions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val selected = selectedMood == option.label
                    Card(
                        onClick = { selectedMood = option.label; saved = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected) option.accent.soft else HealthPalette.Surface),
                        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) option.accent.strong else HealthPalette.Line)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.emoji, fontSize = 28.sp)
                            Text(option.label, style = MaterialTheme.typography.labelMedium, color = if (selected) option.accent.strong else HealthPalette.Ink, maxLines = 1)
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
                    Text("Mức căng thẳng", style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
                    Pill("${stress.toInt()}/10", stressAccent)
                }
                Slider(
                    value = stress, onValueChange = { stress = it; saved = false }, valueRange = 0f..10f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = stressAccent.strong, activeTrackColor = stressAccent.strong, inactiveTrackColor = stressAccent.soft, activeTickColor = Color.White.copy(alpha = .6f), inactiveTickColor = stressAccent.strong.copy(alpha = .3f))
                )
                Text("0 = thư thái · 10 = rất căng thẳng", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ghi chú riêng tư (tùy chọn)") },
                    placeholder = { Text("Điều gì đã ảnh hưởng đến bạn hôm nay?") },
                    minLines = 3,
                    maxLines = 6,
                    shape = MaterialTheme.shapes.medium
                )
                Text("Ghi chú được lưu trong bộ nhớ riêng của ứng dụng trên thiết bị này.", style = MaterialTheme.typography.labelSmall, color = HealthPalette.Muted)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    if (saved) "Đã lưu hôm nay ✓" else "Lưu check-in",
                    onClick = {
                        val updated = listOf(MoodEntry(today, selectedMood, stress.toInt(), note.trim())) + entries.filterNot { it.date == today }
                        writeEntries(context, updated)
                        entries = updated
                        saved = true
                    },
                    enabled = selectedMood.isNotBlank(),
                    color = HealthPalette.Pink.strong
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        GradientCard(HealthPalette.Sky.gradient, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Thở cùng nhịp 4–4", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Hít vào 4 giây, thở ra 4 giây trong 1 phút.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
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
            val phase = if ((60 - secondsLeft) % 8 < 4) "Hít vào" else "Thở ra"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (breathing) "$phase · ${secondsLeft}s" else if (secondsLeft == 0) "Đã hoàn thành 🎉" else "Sẵn sàng", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (!breathing && secondsLeft == 0) secondsLeft = 60
                        breathing = !breathing
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HealthPalette.Sky.strong)
                ) { Text(if (breathing) "Tạm dừng" else if (secondsLeft == 60) "Bắt đầu" else "Tiếp tục", style = MaterialTheme.typography.labelLarge) }
            }
        }

        SectionHeader("Nhật ký gần đây", modifier = Modifier.padding(top = 18.dp))
        if (entries.isEmpty()) {
            Text("Chưa có check-in. Hãy bắt đầu bằng cảm xúc hôm nay.", color = HealthPalette.Muted)
        } else {
            entries.sortedByDescending { it.date }.take(7).forEach { entry ->
                val option = moodOptions.firstOrNull { it.label == entry.mood }
                AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(option?.accent?.soft ?: HealthPalette.SurfaceAlt), contentAlignment = Alignment.Center) {
                            Text(option?.emoji ?: "•", fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${formatDate(entry.date)} · ${entry.mood}", style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                            Text("Căng thẳng ${entry.stress}/10${if (entry.note.isNotBlank()) " · ${entry.note.take(55)}" else ""}", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, maxLines = 2)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Nếu cảm xúc khó chịu kéo dài hoặc ảnh hưởng sinh hoạt, hãy trao đổi với chuyên gia sức khỏe tinh thần. Nội dung trong ứng dụng chỉ để tự theo dõi.", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
        Spacer(Modifier.height(28.dp))
    }
}

private fun formatDate(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM", Locale("vi")))
}.getOrDefault(date)

private fun readEntries(context: Context): List<MoodEntry> = runCatching {
    val json = JSONObject(context.getSharedPreferences("mental_health_private", Context.MODE_PRIVATE).getString("entries", "{}") ?: "{}")
    json.keys().asSequence().map { key ->
        val value = json.getJSONObject(key)
        MoodEntry(key, value.optString("mood"), value.optInt("stress"), value.optString("note"))
    }.toList()
}.getOrDefault(emptyList())

private fun writeEntries(context: Context, entries: List<MoodEntry>) {
    val json = JSONObject()
    entries.forEach { entry ->
        json.put(entry.date, JSONObject().put("mood", entry.mood).put("stress", entry.stress).put("note", entry.note))
    }
    context.getSharedPreferences("mental_health_private", Context.MODE_PRIVATE).edit().putString("entries", json.toString()).apply()
}
