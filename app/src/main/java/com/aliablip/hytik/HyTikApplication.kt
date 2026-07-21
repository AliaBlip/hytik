package com.aliablip.hytik

import android.app.Application
import com.aliablip.hytik.data.db.AppDatabase
import com.aliablip.hytik.data.notification.DownloadNotificationHelper

class HyTikApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Room Database early
        AppDatabase.getInstance(this)
        
        // Create notification channels for download progress
        DownloadNotificationHelper.createChannel(this)
    }
}
