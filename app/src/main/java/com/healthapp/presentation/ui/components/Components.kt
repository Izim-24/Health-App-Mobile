package com.healthapp.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.theme.HealthPalette

val CardShape = RoundedCornerShape(24.dp)

/** White card with a hairline border and no shadow — the base surface for content. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = HealthPalette.Surface,
    border: Boolean = color == HealthPalette.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = color)
    val stroke = if (border) BorderStroke(1.dp, HealthPalette.Line) else null
    val elevation = CardDefaults.cardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = CardShape, colors = colors, border = stroke, elevation = elevation, content = content)
    else Card(modifier = modifier, shape = CardShape, colors = colors, border = stroke, elevation = elevation, content = content)
}

/** Full-bleed gradient card used for hero/summary content. Text on it should be white. */
@Composable
fun GradientCard(brush: Brush, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(CardShape)
            .background(brush)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
        content = content
    )
}

/** Rounded-square icon chip in an area's colour. */
@Composable
fun IconBadge(icon: ImageVector, accent: Accent, size: Dp = 42.dp, onDark: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .background(if (onDark) Color.White.copy(alpha = 0.22f) else accent.soft),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (onDark) Color.White else accent.strong, modifier = Modifier.size(size * 0.52f))
    }
}

/** Small coloured pill label. */
@Composable
fun Pill(text: String, accent: Accent, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.clip(CircleShape).background(accent.soft).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall, color = accent.strong, maxLines = 1
    )
}

/** Screen title block: coloured eyebrow pill, bold title, muted subtitle. */
@Composable
fun ScreenHeader(eyebrow: String, title: String, subtitle: String? = null, accent: Accent = HealthPalette.Emerald, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(eyebrow, accent)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = HealthPalette.Ink)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted) }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, modifier: Modifier = Modifier, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Compact metric tile: icon badge, label, big value, small detail. */
@Composable
fun StatTile(icon: ImageVector, label: String, value: String, detail: String?, accent: Accent, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBadge(icon, accent, size = 36.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleLarge, color = HealthPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Thin rounded progress bar in an accent colour. */
@Composable
fun AccentProgress(progress: Float, accent: Accent, modifier: Modifier = Modifier, track: Color = accent.soft, height: Dp = 8.dp) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(height).clip(CircleShape),
        color = accent.strong, trackColor = track, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
    )
}

/** Standard full-width primary button (pill). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, color: Color = HealthPalette.Primary) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        icon?.let { Icon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Secondary pill button: tinted background, coloured text. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, accent: Accent, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent.soft, contentColor = accent.strong),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Standard top bar for pushed (non-tab) screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(HealthPalette.Surface), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", tint = HealthPalette.Ink)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = HealthPalette.Background, titleContentColor = HealthPalette.Ink),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}
