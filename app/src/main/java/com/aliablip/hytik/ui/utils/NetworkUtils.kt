package com.aliablip.hytik.ui.utils

object NetworkUtils {
    private val TIKTOK_REGEXS = listOf(
        Regex("""https?://(?:www\.)?tiktok\.com/[^\s"'()<>]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:vt|vm|m|t)\.tiktok\.com/[^\s"'()<>]+""", RegexOption.IGNORE_CASE)
    )

    fun containsTikTokUrl(text: String): Boolean {
        return TIKTOK_REGEXS.any { it.containsMatchIn(text) }
    }

    fun extractTikTokUrl(text: String): String? {
        for (rgx in TIKTOK_REGEXS) {
            val match = rgx.find(text)
            if (match != null) {
                var url = match.value.trim()
                url = url.trimEnd('.', ',', '!', ')', ']', '}', '"', '\'')
                return url
            }
        }
        return null
    }
}
