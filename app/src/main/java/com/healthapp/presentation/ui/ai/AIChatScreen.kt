package com.healthapp.presentation.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.viewmodel.AIChatViewModel

private val suggestions = listOf(
    "Huyết áp 130/85 có cao không?",
    "Gợi ý bữa sáng nhiều đạm",
    "Làm sao để ngủ ngon hơn?"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AIChatScreen(onBack: () -> Unit = {}, viewModel: AIChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    val violet = HealthPalette.Violet

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Trợ lý AI", onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (state.messages.isEmpty()) item {
                    GradientCard(violet.gradient, Modifier.fillMaxWidth()) {
                        IconBadge(Icons.Rounded.AutoAwesome, violet, onDark = true)
                        Spacer(Modifier.height(12.dp))
                        Text("Xin chào! Tôi có thể giúp gì?", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "Hỏi về thuật ngữ y tế, thói quen lành mạnh hoặc chuẩn bị câu hỏi cho bác sĩ. Không dùng để chẩn đoán hay đổi thuốc.",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Thử hỏi", style = MaterialTheme.typography.labelLarge, color = HealthPalette.Muted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s ->
                            AssistChip(
                                onClick = { draft = s }, label = { Text(s) }, shape = CircleShape,
                                colors = AssistChipDefaults.assistChipColors(containerColor = violet.soft, labelColor = violet.strong),
                                border = null
                            )
                        }
                    }
                }
                items(state.messages) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        val shape = if (message.fromUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                        Box(
                            Modifier.widthIn(max = 300.dp).clip(shape)
                                .background(if (message.fromUser) violet.strong else HealthPalette.Surface)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(message.text, style = MaterialTheme.typography.bodyMedium, color = if (message.fromUser) Color.White else HealthPalette.Ink)
                        }
                    }
                }
                if (state.sending) item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = violet.strong, trackColor = violet.soft) }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(28.dp)).background(HealthPalette.Surface).padding(start = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    draft, { draft = it.take(2000) }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Nhập câu hỏi…", color = HealthPalette.Subtle) }, maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    )
                )
                val canSend = draft.isNotBlank() && !state.sending
                IconButton(
                    onClick = { viewModel.send(draft); draft = "" }, enabled = canSend,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = violet.strong, contentColor = Color.White, disabledContainerColor = HealthPalette.SurfaceAlt, disabledContentColor = HealthPalette.Subtle)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Gửi câu hỏi", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
