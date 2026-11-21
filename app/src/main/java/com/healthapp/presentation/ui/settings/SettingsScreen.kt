package com.healthapp.presentation.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.exportFile) {
        val file = state.exportFile ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Lưu hoặc chia sẻ bản xuất"))
        viewModel.exportConsumed()
    }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar("Cài đặt & dữ liệu") { navController.popBackStack() }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeSelector(state.themeMode, viewModel::setThemeMode)
            SettingRow(Icons.Default.Notifications, HealthPalette.Amber, "Nhắc nhở", "Uống nước, vận động, bữa ăn, giấc ngủ") { navController.navigate("reminders") }
            SettingRow(Icons.Default.Sync, HealthPalette.Sky, "Health Connect", "Nhập bước chân, giấc ngủ, nhịp tim… từ đồng hồ và ứng dụng khác") { navController.navigate("health_connect") }
            SettingRow(Icons.Default.Medication, HealthPalette.Indigo, "Lịch uống thuốc", "Giờ uống, số ngày dùng, lịch sử liều") { navController.navigate("medications") }
            SettingRow(Icons.Default.FileDownload, HealthPalette.Emerald, "Xuất dữ liệu (JSON)", "Tạo bản sao toàn bộ nhật ký để lưu hoặc gửi cho bác sĩ") { if (!state.busy) viewModel.export() }
            if (com.healthapp.BuildConfig.DEBUG) {
                SettingRow(Icons.Default.Science, HealthPalette.Violet, "Nạp dữ liệu mẫu (demo)", "14 ngày bữa ăn, giấc ngủ, bước chân, chỉ số, thói quen, thuốc — gắn nhãn mẫu") { if (!state.busy) viewModel.seedDemoData() }
                if (state.hasDemoData) SettingRow(Icons.Default.LayersClear, HealthPalette.Amber, "Xóa dữ liệu mẫu", "Chỉ xóa dữ liệu mẫu, giữ nguyên dữ liệu bạn nhập") { if (!state.busy) viewModel.clearDemoData() }
            }
            SettingRow(Icons.Default.DeleteForever, HealthPalette.Coral, "Xóa toàn bộ dữ liệu", "Xóa hồ sơ, nhật ký và nhắc nhở trên thiết bị này", danger = true) { confirmDelete = true }

            AppCard(Modifier.fillMaxWidth(), color = HealthPalette.Emerald.soft) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Shield, null, tint = HealthPalette.Emerald.strong)
                    Text(
                        "Dữ liệu sức khỏe chỉ lưu trên thiết bị (kể cả dữ liệu nhập từ Health Connect). Sao lưu tự động của Android đang tắt. Bản xuất chứa thông tin nhạy cảm — chỉ chia sẻ với người bạn tin tưởng.",
                        style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink
                    )
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, color = HealthPalette.Primary) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa toàn bộ dữ liệu?") },
            text = { Text("Hồ sơ, bữa ăn, thói quen, chỉ số, thuốc, check-in cảm xúc và nhắc nhở sẽ bị xóa vĩnh viễn khỏi thiết bị. Không thể hoàn tác. Hãy xuất dữ liệu trước nếu cần.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAll() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xóa vĩnh viễn") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } }
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, accent: com.healthapp.presentation.ui.theme.Accent, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon, accent)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) accent.strong else HealthPalette.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = HealthPalette.Subtle)
        }
    }
}

/** Giao diện: follow the system, or force light/dark. */
@Composable
private fun ThemeSelector(mode: com.healthapp.domain.model.ThemeMode, onSelect: (com.healthapp.domain.model.ThemeMode) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.DarkMode, HealthPalette.Violet)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Giao diện", style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                    Text("Sáng, tối hoặc theo cài đặt của máy", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.CircleShape).background(HealthPalette.SurfaceAlt).padding(4.dp)
            ) {
                listOf(
                    com.healthapp.domain.model.ThemeMode.SYSTEM to "Theo máy",
                    com.healthapp.domain.model.ThemeMode.LIGHT to "Sáng",
                    com.healthapp.domain.model.ThemeMode.DARK to "Tối"
                ).forEach { (m, label) ->
                    val selected = m == mode
                    Box(
                        Modifier.weight(1f).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (selected) HealthPalette.Violet.strong else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onSelect(m) }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) androidx.compose.ui.graphics.Color.White else HealthPalette.Muted)
                    }
                }
            }
        }
    }
}
