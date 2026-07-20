package com.aliablip.hytik.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.aliablip.hytik.data.engine.EngineMode

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hytik_prefs", Context.MODE_PRIVATE)

    var isAutoPasteEnabled: Boolean
        get() = prefs.getBoolean("key_auto_paste", true)
        set(value) = prefs.edit { putBoolean("key_auto_paste", value) }

    var engineMode: EngineMode
        get() = when (prefs.getString("key_engine_mode", EngineMode.HYBRID_AUTO.name)) {
            EngineMode.TIKWM_HD.name -> EngineMode.TIKWM_HD
            EngineMode.TIKLY_FAST.name -> EngineMode.TIKLY_FAST
            else -> EngineMode.HYBRID_AUTO
        }
        set(value) = prefs.edit { putString("key_engine_mode", value.name) }
}
