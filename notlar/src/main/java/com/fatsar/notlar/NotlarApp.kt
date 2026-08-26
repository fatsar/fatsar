package com.fatsar.notlar

import android.app.Application
import com.google.android.material.color.DynamicColors

/** Samsung/Pixel gibi cihazlarda duvar kağıdı renklerini (Material You) uygular. */
class NotlarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
