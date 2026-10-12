package com.chate.thesis

import android.app.Application
import com.google.android.material.color.DynamicColors

class ThesisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable Monet Dynamic Theming (wallpaper-extracted colors)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
