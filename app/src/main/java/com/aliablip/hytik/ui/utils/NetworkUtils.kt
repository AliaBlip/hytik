package com.aliablip.hytik.ui.utils

object NetworkUtils {
    private val TIKTOK_REGEX = Regex("https?://(?:www\\.|vt\\.|vm\\.|m\\.|t\\.)?tiktok\\.com/[^\\s\"')<>]+")

    fun containsTikTokUrl(text: String): Boolean {
        return TIKTOK_REGEX.containsMatchIn(text)
    }

    fun extractTikTokUrl(text: String): String? {
        return TIKTOK_REGEX.find(text)?.value?.trim()
    }
}
