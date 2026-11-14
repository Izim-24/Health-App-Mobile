package com.healthapp.presentation.ui.dashboard

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
import com.healthapp.presentation.ui.theme.HealthPalette

private fun ScoreArea.route() = when (this) {
    ScoreArea.SLEEP, ScoreArea.ACTIVITY, ScoreArea.BLOOD_PRESSURE -> "health"
    ScoreArea.BMI -> "profile"
    ScoreArea.MEDICATION -> "medications"
    ScoreArea.HABITS -> "habits"
}

private fun ScoreArea.howToAdd() = when (this) {
    ScoreArea.SLEEP -> "Ghi giấc ngủ hoặc kết nối Health Connect"
    ScoreArea.ACTIVITY -> "Ghi số bước hoặc kết nối Health Connect"
    ScoreArea.BLOOD_PRESSURE -> "Ghi một lần đo huyết áp (trong 30 ngày)"
    ScoreArea.BMI -> "Nhập chiều cao và cân nặng trong Hồ sơ"
    ScoreArea.MEDICATION, ScoreArea.HABITS -> ""
}

private fun scoreColor(score: Int) = when {
    score >= 85 -> HealthPalette.Emerald.strong
    score >= 70 -> HealthPalette.Sky.strong
    score >= 50 -> HealthPalette.Amber.strong
    else -> HealthPalette.Coral.strong
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScoreSheet(result: HealthScoreResult?, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HealthPalette.Background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Điểm sức khỏe", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
            if (result?.score != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${result.score}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = scoreColor(result.score))
                    Text(" /100 · ${result.grade}", style = MaterialTheme.typography.titleMedium, color = HealthPalette.Muted, modifier = Modifier.padding(bottom = 6.dp))
                }
            } else {
                Text(
                    "Cần ít nhất ${HealthScore.MIN_AREAS} chỉ số để tính điểm" + (result?.let { " (hiện có ${it.components.size})." } ?: "."),
                    color = HealthPalette.Muted
                )
            }

            result?.components?.forEach { ComponentRow(it, onClick = { onOpen(it.area.route()) }) }

            if (!result?.missing.isNullOrEmpty()) {
                Text("Chưa có dữ liệu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
                result!!.missing.forEach { area ->
                    Card(onClick = { onOpen(area.route()) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Surface), border = androidx.compose.foundation.BorderStroke(1.dp, HealthPalette.Line)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(area.label, fontWeight = FontWeight.SemiBold, color = HealthPalette.Muted)
                                Text(area.howToAdd(), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                            }
                            Text("›", color = HealthPalette.Muted)
                        }
                    }
                }
            }

            Text(
                "Cách tính: mỗi chỉ số được chấm 0–100 theo khuyến nghị công khai — ngủ 7–9 giờ/đêm; khoảng 8.000 bước/ngày; " +
                    "huyết áp theo phân loại ACC/AHA 2017; BMI theo chuẩn châu Á–Thái Bình Dương của WHO (bình thường 18,5–22,9); tỷ lệ uống thuốc và hoàn thành thói quen trong 7 ngày — " +
                    "rồi lấy trung bình có trọng số các chỉ số đang có dữ liệu. Đây là điểm theo dõi thói quen, không phải chẩn đoán y khoa.",
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
                Text(c.area.label, fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
                Text("${c.score}", fontWeight = FontWeight.Bold, color = scoreColor(c.score))
            }
            LinearProgressIndicator(
                progress = { c.score / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = scoreColor(c.score),
                trackColor = HealthPalette.SurfaceAlt
            )
            Text(c.detail, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            c.tip?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink) }
        }
    }
}
