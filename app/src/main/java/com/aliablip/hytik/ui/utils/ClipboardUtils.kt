package com.aliablip.hytik.ui.utils

import android.content.ClipboardManager
import android.content.Context

object ClipboardUtils {
    fun getTikTokUrlFromClipboard(context: Context): String? {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()
                    if (!text.isNullOrBlank() && NetworkUtils.containsTikTokUrl(text)) {
                        return NetworkUtils.extractTikTokUrl(text)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore security exception on Android 10+ background check if any
        }
        return null
    }
}
