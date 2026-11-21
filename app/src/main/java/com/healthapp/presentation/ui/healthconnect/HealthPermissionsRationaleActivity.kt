package com.healthapp.presentation.ui.healthconnect

import com.healthapp.R
import androidx.compose.ui.res.stringResource
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

    // Android 12 and older: use the in-app language choice here too.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.healthapp.core.AppLocale.localized(newBase))
    }
}

@Composable
fun HealthDataPrivacy(onClose: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().background(HealthPalette.Background).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
        Text(stringResource(R.string.privacy_reads), color = HealthPalette.Ink)
        Text(
            stringResource(R.string.privacy_list),
            color = HealthPalette.Ink
        )
        Text(stringResource(R.string.privacy_use), color = HealthPalette.Ink)
        Text(
            stringResource(R.string.privacy_storage),
            color = HealthPalette.Ink
        )
        Text(
            stringResource(R.string.privacy_revoke),
            color = HealthPalette.Muted, style = MaterialTheme.typography.bodySmall
        )
        onClose?.let { Button(onClick = it, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.got_it)) } }
    }
}
