package com.aliablip.hytik

import android.app.Application
import com.aliablip.hytik.data.db.AppDatabase

class HyTikApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Room Database early
        AppDatabase.getInstance(this)
    }
}
