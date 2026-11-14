package com.healthapp.presentation.ui.healthconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthapp.presentation.ui.theme.HealthAppTheme
import com.healthapp.presentation.ui.theme.HealthPalette

/**
 * Required by Health Connect: explains what the app reads and why. Opened from the
 * permission dialog's privacy link and from Android's "permission usage" settings.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HealthAppTheme { HealthDataPrivacy(onClose = ::finish) } }
    }
}

@Composable
fun HealthDataPrivacy(onClose: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().background(HealthPalette.Background).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Quyền riêng tư dữ liệu sức khỏe", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
        Text("Ứng dụng chỉ ĐỌC các loại dữ liệu bạn cho phép từ Health Connect:", color = HealthPalette.Ink)
        Text(
            "• Số bước và năng lượng vận động — tổng theo ngày\n" +
                "• Giấc ngủ — giờ đi ngủ, giờ thức dậy\n" +
                "• Nhịp tim — trung bình theo từng giờ\n" +
                "• Cân nặng, huyết áp, đường huyết",
            color = HealthPalette.Ink
        )
        Text("Dữ liệu được dùng để hiển thị bảng điều khiển, biểu đồ và báo cáo trong ứng dụng.", color = HealthPalette.Ink)
        Text(
            "Dữ liệu chỉ được lưu trên thiết bị này. Ứng dụng không ghi vào Health Connect, không gửi dữ liệu sức khỏe lên máy chủ, " +
                "không dùng cho quảng cáo và không chia sẻ với bên thứ ba. Trợ lý AI chỉ nhận nội dung bạn tự gõ.",
            color = HealthPalette.Ink
        )
        Text(
            "Bạn có thể thu hồi quyền bất cứ lúc nào trong Health Connect, hoặc trong ứng dụng: Cài đặt → Health Connect → Ngắt kết nối " +
                "(có tùy chọn xóa dữ liệu đã nhập).",
            color = HealthPalette.Muted, style = MaterialTheme.typography.bodySmall
        )
        onClose?.let { Button(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Đã hiểu") } }
    }
}
