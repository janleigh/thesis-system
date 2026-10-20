package com.chate.thesis

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val id: String, val title: String, val nightMode: Int) {
    SYSTEM("system", "Automatic", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", "Light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", "Dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromId(id: String?): ThemeMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
        }
    }
}

enum class ColorPalette(val id: String, val title: String) {
    MONET("monet", "Monet (Dynamic)"),
    CYAN("cyan", "Electric Cyan"),
    BLUE("blue", "Cyber Blue"),
    EMERALD("emerald", "Emerald Forest"),
    AMBER("amber", "Sunset Amber"),
    VIOLET("violet", "Neon Violet"),
    CRIMSON("crimson", "Crimson Rose");

    companion object {
        fun fromId(id: String?): ColorPalette {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MONET
        }
    }

    fun getThemeResId(): Int = when (this) {
        MONET -> R.style.Theme_Thesis
        CYAN -> R.style.Theme_Thesis_Palette_Cyan
        BLUE -> R.style.Theme_Thesis_Palette_Blue
        EMERALD -> R.style.Theme_Thesis_Palette_Emerald
        AMBER -> R.style.Theme_Thesis_Palette_Amber
        VIOLET -> R.style.Theme_Thesis_Palette_Violet
        CRIMSON -> R.style.Theme_Thesis_Palette_Crimson
    }
}

enum class HeaderEffect(val id: String, val title: String) {
    NOTHING("nothing", "Nothing (Still)"),
    SNOW("snow", "Snow"),
    MAZE("maze", "Maze"),
    CIRCUIT("circuit", "Circuit"),
    CODE_RAIN("code_rain", "Code Rain");

    companion object {
        fun fromId(id: String?): HeaderEffect {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NOTHING
        }
    }
}

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.id))
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.id).apply()

    var palette: ColorPalette
        get() = ColorPalette.fromId(prefs.getString(KEY_PALETTE, ColorPalette.MONET.id))
        set(value) = prefs.edit().putString(KEY_PALETTE, value.id).apply()

    var isAmoledBlackEnabled: Boolean
        get() = prefs.getBoolean(KEY_AMOLED_BLACK, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED_BLACK, value).apply()

    var headerEffect: HeaderEffect
        get() = HeaderEffect.fromId(prefs.getString(KEY_HEADER_EFFECT, HeaderEffect.NOTHING.id))
        set(value) = prefs.edit().putString(KEY_HEADER_EFFECT, value.id).apply()

    var promptKeyboardSwitchOnDisable: Boolean
        get() = prefs.getBoolean(KEY_PROMPT_KEYBOARD_SWITCH, true)
        set(value) = prefs.edit().putBoolean(KEY_PROMPT_KEYBOARD_SWITCH, value).apply()

    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
    }

    companion object {
        private const val PREFS_NAME = "pukey_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PALETTE = "theme_palette"
        private const val KEY_AMOLED_BLACK = "amoled_black"
        private const val KEY_HEADER_EFFECT = "header_effect"
        private const val KEY_PROMPT_KEYBOARD_SWITCH = "prompt_keyboard_switch"
    }
}

