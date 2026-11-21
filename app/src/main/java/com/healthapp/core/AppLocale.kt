package com.healthapp.core

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language (Vietnamese is the default resource set, English in values-en).
 *
 * - Android 13+: the framework's per-app locale ([LocaleManager]); the choice also appears in
 *   system Settings → Apps → Language, and the system recreates the activity.
 * - Older versions: the choice is stored here and applied by wrapping the base context
 *   (MainActivity.attachBaseContext, and [localized] for notifications from workers).
 *
 * Lives outside presentation/data because both layers need it (UI and notifications).
 */
object AppLocale {
    /** Follow the phone's language. */
    const val SYSTEM = ""
    val SUPPORTED = listOf("vi", "en")

    private const val PREFS = "app_locale"
    private const val KEY = "tag"

    /** "" (system), "vi" or "en". */
    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeIf { !it.isEmpty }?.get(0)?.language?.takeIf { it in SUPPORTED } ?: SYSTEM
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
        }

    fun set(activity: Activity, tag: String) {
        if (tag == current(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            // The system recreates the activity with the new configuration.
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            activity.recreate()
        }
    }

    /** Context whose resources use the chosen language (no-op on Android 13+, where the framework does it). */
    fun localized(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM)
        if (tag.isNullOrEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
