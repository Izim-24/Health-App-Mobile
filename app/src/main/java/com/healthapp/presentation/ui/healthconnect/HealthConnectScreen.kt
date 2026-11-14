package com.healthapp.presentation.ui.healthconnect

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.healthapp.data.healthconnect.HcAvailability
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.viewmodel.HealthConnectViewModel
import java.time.format.DateTimeFormatter

private val dataTypes = listOf(
    HealthPermission.getReadPermission(StepsRecord::class) to "Số bước",
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) to "Năng lượng vận động",
    HealthPermission.getReadPermission(SleepSessionRecord::class) to "Giấc ngủ",
    HealthPermission.getReadPermission(HeartRateRecord::class) to "Nhịp tim",
    HealthPermission.getReadPermission(WeightRecord::class) to "Cân nặng",
    HealthPermission.getReadPermission(BloodPressureRecord::class) to "Huyết áp",
    HealthPermission.getReadPermission(BloodGlucoseRecord::class) to "Đường huyết"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectScreen(navController: NavController, viewModel: HealthConnectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmDisconnect by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(viewModel.permissionContract) { viewModel.onPermissionsResult(it) }

    // Permissions can change in Health Connect while we're in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar("Health Connect") { navController.popBackStack() }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Nhập số bước, giấc ngủ, nhịp tim, cân nặng, huyết áp và đường huyết từ đồng hồ, vòng đeo hoặc các ứng dụng sức khỏe khác qua Health Connect. Chỉ đọc, dữ liệu lưu trên thiết bị.",
                color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium
            )

            when (state.availability) {
                HcAvailability.NOT_SUPPORTED -> InfoCard("Thiết bị chưa hỗ trợ Health Connect", "Health Connect cần Android 9 trở lên. Trên Android 13 trở xuống, hãy cài ứng dụng Health Connect từ Google Play.")
                HcAvailability.NEEDS_UPDATE -> {
                    InfoCard("Cần cài hoặc cập nhật Health Connect", "Mở Google Play để cài đặt/cập nhật, rồi quay lại đây.")
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Mở Google Play") }
                }
                HcAvailability.AVAILABLE -> {
                    StatusCard(state.connected, state.syncing, state.lastSync?.format(DateTimeFormatter.ofPattern("HH:mm dd/MM")), state.lastSummary?.let {
                        "Lần gần nhất: ${it.days} ngày bước chân, ${it.sleep} giấc ngủ, ${it.heartRate} giờ nhịp tim, ${it.weight} cân nặng, ${it.bloodPressure} huyết áp, ${it.glucose} đường huyết."
                    })

                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Loại dữ liệu", fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
                            dataTypes.forEach { (perm, label) ->
                                val ok = perm in state.granted
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (ok) HealthPalette.Sky.strong else HealthPalette.Subtle, modifier = Modifier.size(20.dp))
                                    Text(label, color = if (ok) HealthPalette.Ink else HealthPalette.Muted)
                                }
                            }
                        }
                    }

                    if (!state.connected || state.granted.size < viewModel.permissions.size) {
                        PrimaryButton(if (state.connected) "Cấp thêm quyền" else "Kết nối Health Connect", { launcher.launch(viewModel.permissions) }, color = HealthPalette.Sky.strong)
                    }
                    if (state.connected) {
                        OutlinedButton(onClick = viewModel::syncNow, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("Đồng bộ ngay")
                        }
                        TextButton(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text("Ngắt kết nối")
                        }
                    }
                    Text(
                        "Tự đồng bộ khi mở ứng dụng (tối đa 15 phút/lần), lấy 30 ngày gần nhất ở lần đầu. Số bước nhập từ Health Connect được cộng cùng số bước bạn tự nhập trong ngày.",
                        style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted
                    )
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showPrivacy = true }) { Text("Dữ liệu được dùng thế nào?") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDisconnect) {
        var removeImported by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Ngắt kết nối Health Connect?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ứng dụng sẽ ngừng đồng bộ và thu hồi quyền đọc. Dữ liệu trong Health Connect không bị ảnh hưởng.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = removeImported, onCheckedChange = { removeImported = it })
                        Text("Xóa dữ liệu đã nhập vào ứng dụng (giữ dữ liệu bạn tự nhập)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.disconnect(removeImported); confirmDisconnect = false }) { Text("Ngắt kết nối") } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Hủy") } }
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            text = { Box(Modifier.heightIn(max = 480.dp)) { HealthDataPrivacy() } },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Đóng") } }
        )
    }
}

@Composable
private fun StatusCard(connected: Boolean, syncing: Boolean, lastSync: String?, summary: String?) {
    AppCard(Modifier.fillMaxWidth(), color = if (connected) HealthPalette.Sky.strong else HealthPalette.Surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val fg = if (connected) HealthPalette.Surface else HealthPalette.Ink
            Text(if (connected) "ĐÃ KẾT NỐI" else "CHƯA KẾT NỐI", color = if (connected) HealthPalette.Emerald.soft else HealthPalette.Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(
                when {
                    syncing -> "Đang đồng bộ…"
                    lastSync != null -> "Đồng bộ lúc $lastSync"
                    connected -> "Chưa đồng bộ"
                    else -> "Kết nối để nhập dữ liệu tự động"
                },
                color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = HealthPalette.Emerald.soft)
            summary?.let { Text(it, color = fg.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Orange.soft)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink)
        }
    }
}
