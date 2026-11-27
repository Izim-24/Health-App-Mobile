package com.healthapp.presentation.ui.health

/** Apps that commonly write to Health Connect, by package name. */
private val KNOWN_APPS = mapOf(
    "com.sec.android.app.shealth" to "Samsung Health",
    "com.google.android.apps.fitness" to "Google Fit",
    "com.fitbit.FitbitMobile" to "Fitbit",
    "com.garmin.android.apps.connectmobile" to "Garmin Connect",
    "com.mi.health" to "Mi Fitness",
    "com.xiaomi.wearable" to "Mi Fitness",
    "com.huawei.health" to "Huawei Health",
    "jp.co.omron.healthcare.omron_connect" to "OMRON connect",
    "com.withings.wiscale2" to "Withings",
    "com.zepp.health" to "Zepp",
    "com.huami.watch.hmwatchmanager" to "Zepp",
    "com.healthapp" to "Health App (mẫu)"
)

/**
 * The app that wrote an imported reading, from its `source` ("HEALTH_CONNECT:<package>"); null when unknown
 * (plain "HEALTH_CONNECT", typed or sample data). Unknown packages show their last name part, capitalised.
 * A fixed list instead of PackageManager: Android 11+ hides other apps unless declared, and this stays testable.
 */
fun hcAppName(source: String?): String? {
    val pkg = source?.takeIf { it.startsWith("HEALTH_CONNECT:") }?.substringAfter(':')?.takeIf { it.isNotBlank() } ?: return null
    return KNOWN_APPS[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
