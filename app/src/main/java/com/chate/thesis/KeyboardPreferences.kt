package com.chate.thesis

import android.content.Context
import android.content.SharedPreferences

class KeyboardPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "thesis_keyboard_prefs"
        const val KEY_HEIGHT_DP = "keyboard_height_dp"
        const val KEY_LONG_PRESS_MODE = "long_press_mode" // "VARIANTS" vs "RAPID_FIRE"
        const val KEY_LONG_PRESS_DELAY = "long_press_delay_ms"
        const val KEY_REPEAT_INTERVAL = "repeat_interval_ms"
        const val KEY_SHOW_KEY_HINTS = "show_key_hints"

        const val DEFAULT_HEIGHT_DP = 280
        const val MIN_HEIGHT_DP = 190
        const val MAX_HEIGHT_DP = 420

        const val MODE_VARIANTS = "VARIANTS"
        const val MODE_RAPID_FIRE = "RAPID_FIRE"

        const val DEFAULT_LONG_PRESS_DELAY = 350L // ms
        const val DEFAULT_REPEAT_INTERVAL = 50L // ms

        const val ACTION_SETTINGS_CHANGED = "com.chate.thesis.KEYBOARD_SETTINGS_CHANGED"
    }

    var heightDp: Int
        get() = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
        set(value) = prefs.edit().putInt(KEY_HEIGHT_DP, value.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)).apply()

    var longPressMode: String
        get() = prefs.getString(KEY_LONG_PRESS_MODE, MODE_VARIANTS) ?: MODE_VARIANTS
        set(value) = prefs.edit().putString(KEY_LONG_PRESS_MODE, value).apply()

    var longPressDelayMs: Long
        get() = prefs.getLong(KEY_LONG_PRESS_DELAY, DEFAULT_LONG_PRESS_DELAY)
        set(value) = prefs.edit().putLong(KEY_LONG_PRESS_DELAY, value).apply()

    var repeatIntervalMs: Long
        get() = prefs.getLong(KEY_REPEAT_INTERVAL, DEFAULT_REPEAT_INTERVAL)
        set(value) = prefs.edit().putLong(KEY_REPEAT_INTERVAL, value).apply()

    var showKeyHints: Boolean
        get() = prefs.getBoolean(KEY_SHOW_KEY_HINTS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_KEY_HINTS, value).apply()
}

