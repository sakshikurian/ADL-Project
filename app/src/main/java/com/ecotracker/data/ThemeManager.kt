package com.ecotracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val PREFS_NAME = "eco_theme_prefs"
    private const val KEY_DARK_MODE = "is_dark_mode_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns true if dark mode is enabled, false if light theme.
     * Persisted independently of user session so it survives login/logout.
     */
    fun isDarkModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, false)
    }

    /**
     * Toggles dark mode immediately and persists the preference.
     */
    fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        applyTheme(enabled)
    }

    /**
     * Applies the saved theme immediately across the entire app.
     */
    fun applySavedTheme(context: Context) {
        val isDark = isDarkModeEnabled(context)
        applyTheme(isDark)
    }

    private fun applyTheme(isDark: Boolean) {
        val targetMode = if (isDark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }
}
