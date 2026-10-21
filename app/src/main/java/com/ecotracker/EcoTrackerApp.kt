package com.ecotracker

import android.app.Application
import com.ecotracker.data.ThemeManager

class EcoTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply saved light/dark mode preference immediately on process launch
        ThemeManager.applySavedTheme(this)
    }
}
