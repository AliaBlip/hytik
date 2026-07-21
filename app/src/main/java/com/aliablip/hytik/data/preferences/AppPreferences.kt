package com.aliablip.hytik.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.aliablip.hytik.data.engine.EngineMode

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hytik_prefs_v2", Context.MODE_PRIVATE)

    var isAutoPasteEnabled: Boolean
        get() = prefs.getBoolean("key_auto_paste", true)
        set(value) = prefs.edit { putBoolean("key_auto_paste", value) }

    var engineMode: EngineMode
        get() {
            val stored = prefs.getString("key_engine_mode", EngineMode.SMART_AUTO.name)
            return EngineMode.fromStoredName(stored)
        }
        set(value) = prefs.edit { putString("key_engine_mode", value.name) }

    // Additional prefs for permission pro UX
    var hasAskedMediaPermission: Boolean
        get() = prefs.getBoolean("has_asked_media_perm", false)
        set(value) = prefs.edit { putBoolean("has_asked_media_perm", value) }

    var hasAskedNotifPermission: Boolean
        get() = prefs.getBoolean("has_asked_notif_perm", false)
        set(value) = prefs.edit { putBoolean("has_asked_notif_perm", value) }

    var lastDownloadPath: String
        get() = prefs.getString("last_download_path", "Movies/HyTik") ?: "Movies/HyTik"
        set(value) = prefs.edit { putString("last_download_path", value) }
}
