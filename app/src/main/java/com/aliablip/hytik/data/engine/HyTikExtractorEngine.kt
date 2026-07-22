package com.aliablip.hytik.data.engine

import com.aliablip.hytik.data.api.ApiClient
import com.aliablip.hytik.data.api.models.MediaType
import com.aliablip.hytik.data.api.models.TikTokMediaResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

enum class EngineMode {
    SMART_AUTO,
    ULTRA_HD,
    TURBO_FAST;

    fun getDisplayName(): String = when (this) {
        SMART_AUTO -> "HyTik Smart Engine"
        ULTRA_HD -> "HyTik Ultra HD"
        TURBO_FAST -> "HyTik Turbo Fast"
    }

    fun getShortLabel(): String = when (this) {
        SMART_AUTO -> "⚡ SMART AUTO"
        ULTRA_HD -> "💎 ULTRA HD"
        TURBO_FAST -> "🚀 TURBO FAST"
    }

    fun getDescription(): String = when (this) {
        SMART_AUTO -> "Otomatis memilih jalur optimal dengan AI Hybrid - Direkomendasikan"
        ULTRA_HD -> "Kualitas tertinggi hingga 4K, jernih tanpa watermark"
        TURBO_FAST -> "Ekstraksi tercepat, cocok untuk preview instan"
    }

    companion object {
        fun fromStoredName(name: String?): EngineMode {
            return when (name?.uppercase()) {
                "HYBRID_AUTO", "SMART_AUTO", "SMART", "HYCORE_SMART", "AUTO" -> SMART_AUTO
                "TIKWM_HD", "ULTRA_HD", "ULTRA", "HYCORE_ULTRA", "HD" -> ULTRA_HD
                "TIKLY_FAST", "TURBO_FAST", "TURBO", "HYCORE_TURBO", "FAST" -> TURBO_FAST
                else -> SMART_AUTO
            }
        }
    }
}

class HyTikExtractorEngine {

    private val client = ApiClient.okHttpClient

    suspend fun extractMedia(inputUrl: String, mode: EngineMode = EngineMode.SMART_AUTO): Result<TikTokMediaResult> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = extractAndCleanUrl(inputUrl)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Tautan TikTok tidak valid! Pastikan link mengandung tiktok.com, vt.tiktok.com, atau vm.tiktok.com")
                    )
                val resolvedUrl = resolveShortLink(cleanUrl)
                when (mode) {
                    EngineMode.ULTRA_HD -> {
                        val ultra = runCatching { extractViaUltraEngine(resolvedUrl) }.getOrNull()
                        if (ultra != null && ultra.hasPlayableContent()) {
                            return@withContext Result.success(ultra)
                        }
                        val smartFallback = runCatching { extractViaSmartHybrid(resolvedUrl) }.getOrNull()
                        if (smartFallback != null) return@withContext Result.success(smartFallback)
                        Result.failure(Exception("Gagal memuat dengan ${mode.getDisplayName()}. Coba gunakan Smart Auto atau periksa tautan."))
                    }
                    EngineMode.TURBO_FAST -> {
                        val turbo = runCatching { extractViaTurboEngine(resolvedUrl) }.getOrNull()
                        if (turbo != null && turbo.hasPlayableContent()) {
                            return@withContext Result.success(turbo)
                        }
                        val smartFallback = runCatching { extractViaSmartHybrid(resolvedUrl) }.getOrNull()
                        if (smartFallback != null) return@withContext Result.success(smartFallback)
                        Result.failure(Exception("Gagal memuat dengan ${mode.getDisplayName()}. Coba gunakan Smart Auto."))
                    }
                    EngineMode.SMART_AUTO -> {
                        val result = extractViaSmartHybrid(resolvedUrl)
                        if (result != null) Result.success(result)
                        else Result.failure(Exception("Semua jalur ekstraksi HyTik gagal. Pastikan video tidak diprivat dan tautan masih aktif."))
                    }
                }
            } catch (e: Exception) {
                Result.failure(Exception("Terjadi kesalahan sistem HyTik: ${e.message}"))
            }
        }
    }

    private fun TikTokMediaResult.hasPlayableContent(): Boolean {
        return !videoHdNoWmUrl.isNullOrBlank() || !videoNoWmUrl.isNullOrBlank() || !videoWmUrl.isNullOrBlank() || photoUrls.isNotEmpty() || !audioMp3Url.isNullOrBlank()
    }

    // ---------- URL NORMALIZER - FIX SCHEME ERROR ----------
    private fun normalizeUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var url = raw.trim()
        url = unescapeJsonString(url)
        url = url.replace("&amp;", "&").replace("\\u0026amp;", "&")
        url = url.trim()

        if (url.isBlank()) return null

        // filter out obvious non-urls like /video/xxx , /@user , etc.
        if (url.startsWith("/") && !url.startsWith("//")) {
            return null
        }

        // protocol-relative //xxx -> https://xxx
        if (url.startsWith("//")) {
            url = "https:$url"
        }

        // must start with http(s)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // try to recover if it contains known cdn domains but missing scheme
            if (url.contains("tiktokcdn") || url.contains("muscdn") || url.contains("tiktok.com") || url.contains("byteoversea") || url.contains("ibytedtos") || url.contains("tiktokcdn-us") || url.contains("tiktokcdn-eu")) {
                // if looks like domain.com/path but no scheme
                if (!url.contains("://")) {
                    url = "https://$url"
                } else {
                    return null
                }
            } else {
                return null
            }
        }

        // final check: must be absolute http url, no spaces
        if (url.contains(" ")) return null
        if (!url.startsWith("http")) return null

        // reject if still contains /video/xxx as path without domain mp4? Actually tiktok video page urls are not direct downloadable, but they start with https://www.tiktok.com/@.../video/...
        // Such urls should be rejected for download - only allow cdn or direct file urls for video
        // However for safety, we allow www.tiktok.com page urls only if it's used as fallback? For download we need cdn.
        // We'll allow any https url for now, but filter later: if url contains "/video/" and contains "tiktok.com/@" and not containing ".mp4" and not "tiktokcdn", it's a page url, not downloadable.
        if (url.contains("tiktok.com/@") && url.contains("/video/") && !url.contains("tiktokcdn") && !url.contains(".mp4") && !url.contains("muscdn")) {
            // This is a page URL, not a direct file URL, reject for download link
            return null
        }

        return url
    }

    private fun normalizeUrlList(rawList: List<String>): List<String> {
        return rawList.mapNotNull { normalizeUrl(it) }.distinct()
    }

    // ---------- URL Cleaning & Redirect Resolver ----------

    private fun extractAndCleanUrl(text: String): String? {
        if (text.isBlank()) return null
        val regexList = listOf(
            Regex("""https?://(?:www\.)?tiktok\.com/[^\s"'()<>]+""", RegexOption.IGNORE_CASE),
            Regex("""https?://(?:vt|vm|m|t)\.tiktok\.com/[^\s"'()<>]+""", RegexOption.IGNORE_CASE),
            Regex("""https?://(?:www\.)?tiktok\.com/@[^\s"'()<>]+/video/\d+[^\s"'()<>]*""", RegexOption.IGNORE_CASE),
            Regex("""https?://(?:www\.)?tiktok\.com/@[^\s"'()<>]+/photo/\d+[^\s"'()<>]*""", RegexOption.IGNORE_CASE)
        )
        for (rgx in regexList) {
            val match = rgx.find(text)
            if (match != null) {
                var url = match.value.trim()
                url = url.trimEnd('.', ',', '!', ')', ']', '}', '"', '\'')
                return url
            }
        }
        val generic = Regex("""https?://[^\s]+\.tiktok\.com/[^\s]+""", RegexOption.IGNORE_CASE).find(text)
        return generic?.value?.trim()?.trimEnd('.', ',', ')')
    }

    private fun resolveShortLink(originalUrl: String): String {
        val lower = originalUrl.lowercase()
        val needResolve = lower.contains("vt.tiktok.com") || lower.contains("vm.tiktok.com") || lower.contains("t.tiktok.com") || lower.contains("/t/")
        if (!needResolve) return originalUrl

        try {
            val request = Request.Builder()
                .url(originalUrl)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36 TikTok 32.1.3")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            var finalUrlFound: String? = null
            var canonicalFound: String? = null
            var ogFound: String? = null
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl.isNotBlank() && finalUrl != originalUrl && finalUrl.contains("tiktok.com")) {
                    finalUrlFound = finalUrl
                    return@use
                }
                try {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.contains("tiktok.com")) {
                        val canonical = Regex("""<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(bodyStr)?.groupValues?.get(1)
                        if (!canonical.isNullOrBlank() && canonical.contains("tiktok.com")) {
                            canonicalFound = canonical
                            return@use
                        }
                        val ogUrl = Regex("""<meta[^>]+property=["']og:url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(bodyStr)?.groupValues?.get(1)
                        if (!ogUrl.isNullOrBlank() && ogUrl.contains("tiktok.com")) {
                            ogFound = ogUrl
                        }
                    }
                } catch (_: Exception) {}
            }
            if (finalUrlFound != null) return finalUrlFound!!
            if (canonicalFound != null) return canonicalFound!!
            if (ogFound != null) return ogFound!!
        } catch (_: Exception) {}

        try {
            val doc = Jsoup.connect(originalUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36")
                .timeout(12000)
                .followRedirects(true)
                .get()
            val canon = doc.selectFirst("link[rel=canonical]")?.attr("href")
            if (!canon.isNullOrBlank() && canon.contains("tiktok.com")) return canon
            val og = doc.selectFirst("meta[property=og:url]")?.attr("content")
            if (!og.isNullOrBlank() && og.contains("tiktok.com")) return og
            val loc = doc.location()
            if (loc.isNotBlank() && loc != originalUrl && loc.contains("tiktok.com")) return loc
        } catch (_: Exception) {}

        return originalUrl
    }

    private fun extractViaSmartHybrid(url: String): TikTokMediaResult? {
        try {
            val ultra = extractViaUltraEngineInternal(url)
            if (ultra != null && ultra.hasPlayableContent()) return ultra
        } catch (_: Exception) {}
        try {
            val turbo = extractViaTurboEngineInternal(url)
            if (turbo != null && turbo.hasPlayableContent()) return turbo
        } catch (_: Exception) {}
        try {
            val web = extractViaWebCoreEngine(url)
            if (web != null && web.hasPlayableContent()) return web
        } catch (_: Exception) {}
        try {
            val alt = extractViaAlternateApis(url)
            if (alt != null && alt.hasPlayableContent()) return alt
        } catch (_: Exception) {}
        return null
    }

    fun extractViaUltraEngine(url: String): TikTokMediaResult? = extractViaUltraEngineInternal(url)

    private fun extractViaUltraEngineInternal(url: String): TikTokMediaResult? {
        val endpoints = listOf(
            "https://www.tikwm.com/api/",
            "https://tikwm.com/api/",
            "https://www.tikwm.com/api/feed/search",
            "https://tikwm.com/api/feed/search"
        )
        for (endpoint in endpoints) {
            var resultFound: TikTokMediaResult? = null
            try {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val fullUrl = if (endpoint.contains("feed/search")) "$endpoint?url=$encoded" else "$endpoint?url=$encoded&hd=1&count=12&cursor=0&web=1&from=hytik"
                val request = Request.Builder()
                    .url(fullUrl)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/121 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://www.tikwm.com/")
                    .header("Origin", "https://www.tikwm.com")
                    .build()
                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use
                    val bodyStr = resp.body?.string() ?: return@use
                    if (bodyStr.isBlank()) return@use
                    val parsed = parseTikWmJson(bodyStr)
                    if (parsed != null && parsed.hasPlayableContent()) {
                        resultFound = parsed
                    }
                }
                if (resultFound != null) return resultFound
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseTikWmJson(jsonStr: String): TikTokMediaResult? {
        try {
            val root = JSONObject(jsonStr)
            val code = root.optInt("code", 0)
            if (root.has("code") && code != 0) {
                if (!root.has("data")) return null
            }
            val dataObj = when {
                root.has("data") && root.opt("data") is JSONObject -> root.getJSONObject("data")
                root.has("data") -> root.optJSONObject("data")
                else -> root
            } ?: return null

            val realData = if (dataObj.has("data") && dataObj.opt("data") is JSONObject) dataObj.getJSONObject("data") else dataObj

            val id = realData.optString("id", System.currentTimeMillis().toString())
            val title = realData.optString("title", "").takeIf { it.isNotBlank() } ?: "TikTok Video by HyTik"

            var coverRaw = realData.optString("origin_cover", "").ifBlank { realData.optString("cover", "") }.ifBlank { realData.optString("ai_dynamic_cover", "") }
            var playRaw = realData.optString("play", "")
            var wmplayRaw = realData.optString("wmplay", "")
            var hdplayRaw = realData.optString("hdplay", "")
            if (playRaw.isBlank()) playRaw = realData.optString("hdplay", "")
            if (playRaw.isBlank()) playRaw = realData.optString("playAddr", "")

            var musicUrlRaw = realData.optString("music", "").ifBlank {
                val musicInfo = realData.optJSONObject("music_info")
                musicInfo?.optString("play", "") ?: ""
            }

            val authorObj = realData.optJSONObject("author")
            val authorName = authorObj?.optString("nickname", "")?.ifBlank { authorObj.optString("unique_id", "") } ?: "TikTok Creator"
            val authorHandle = authorObj?.optString("unique_id", "")?.let { if (it.isNotBlank()) "@$it" else "@tiktok_user" } ?: "@tiktok_user"
            var authorAvatarRaw = authorObj?.optString("avatar", "") ?: ""

            val playCount = realData.optLong("play_count", 0)
            val diggCount = realData.optLong("digg_count", 0)
            val commentCount = realData.optLong("comment_count", 0)

            // normalize
            val cover = normalizeUrl(coverRaw) ?: ""
            val play = normalizeUrl(playRaw) ?: ""
            val wmplay = normalizeUrl(wmplayRaw) ?: ""
            val hdplay = normalizeUrl(hdplayRaw) ?: ""
            val musicUrl = normalizeUrl(musicUrlRaw) ?: ""
            val authorAvatar = normalizeUrl(authorAvatarRaw) ?: authorAvatarRaw // avatar boleh kosong

            val imagesArray = mutableListOf<String>()
            if (realData.has("images")) {
                val imgVal = realData.opt("images")
                if (imgVal is org.json.JSONArray) {
                    for (i in 0 until imgVal.length()) {
                        val u = normalizeUrl(imgVal.optString(i, "")) ?: continue
                        imagesArray.add(u)
                    }
                } else if (imgVal is String) {
                    normalizeUrl(imgVal)?.let { imagesArray.add(it) }
                }
            }
            if (imagesArray.isEmpty() && realData.has("images")) {
                try {
                    val arr = realData.getJSONArray("images")
                    for (i in 0 until arr.length()) {
                        normalizeUrl(arr.getString(i))?.let { imagesArray.add(it) }
                    }
                } catch (_: Exception) {}
            }

            val filteredImages = normalizeUrlList(imagesArray)

            val mediaType = when {
                filteredImages.isNotEmpty() -> MediaType.PHOTO_CAROUSEL
                play.isNotBlank() || hdplay.isNotBlank() || wmplay.isNotBlank() -> MediaType.VIDEO
                musicUrl.isNotBlank() -> MediaType.AUDIO_ONLY
                else -> MediaType.VIDEO
            }

            val finalHd = hdplay.ifBlank { play }.ifBlank { wmplay }
            val finalNoWm = play.ifBlank { hdplay }.ifBlank { wmplay }
            val finalWm = wmplay.ifBlank { play }.ifBlank { hdplay }

            if (finalHd.isBlank() && finalNoWm.isBlank() && finalWm.isBlank() && filteredImages.isEmpty() && musicUrl.isBlank()) {
                return null
            }

            return TikTokMediaResult(
                id = id,
                title = title,
                authorName = authorName,
                authorHandle = authorHandle,
                authorAvatarUrl = authorAvatar,
                coverUrl = cover,
                videoNoWmUrl = finalNoWm.takeIf { it.isNotBlank() },
                videoHdNoWmUrl = finalHd.takeIf { it.isNotBlank() },
                videoWmUrl = finalWm.takeIf { it.isNotBlank() },
                audioMp3Url = musicUrl.takeIf { it.isNotBlank() },
                photoUrls = filteredImages,
                mediaType = mediaType,
                durationSec = realData.optInt("duration", 0),
                playCount = playCount,
                likeCount = diggCount,
                commentCount = commentCount
            )
        } catch (_: Exception) {
            return null
        }
    }

    fun extractViaTurboEngine(url: String): TikTokMediaResult? = extractViaTurboEngineInternal(url)

    private fun extractViaTurboEngineInternal(url: String): TikTokMediaResult? {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val endpoints = listOf(
            "https://api.tiklydown.eu.org/api/download?url=$encoded",
            "https://api.tiklydown.eu.org/api/download/v2?url=$encoded",
            "https://tiklydown.eu.org/api/download?url=$encoded"
        )
        for (ep in endpoints) {
            var resultFound: TikTokMediaResult? = null
            try {
                val request = Request.Builder()
                    .url(ep)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/121")
                    .header("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use
                    val bodyStr = resp.body?.string() ?: return@use
                    if (bodyStr.isBlank()) return@use
                    val parsed = parseTikLyJson(bodyStr)
                    if (parsed != null && parsed.hasPlayableContent()) {
                        resultFound = parsed
                    }
                }
                if (resultFound != null) return resultFound
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseTikLyJson(jsonStr: String): TikTokMediaResult? {
        try {
            val root = JSONObject(jsonStr)
            val dataObj = when {
                root.has("result") && root.opt("result") is JSONObject -> root.getJSONObject("result")
                root.has("data") && root.opt("data") is JSONObject -> root.getJSONObject("data")
                else -> root
            }

            val id = dataObj.optString("id", System.currentTimeMillis().toString())
            val title = dataObj.optString("title", "").ifBlank { dataObj.optString("desc", "") }.ifBlank { "TikTok Video by HyTik" }

            val videoObj = dataObj.optJSONObject("video")
            var noWmRaw = videoObj?.optString("noWatermark", "") ?: ""
            var wmRaw = videoObj?.optString("watermark", "") ?: ""
            var coverRaw = videoObj?.optString("cover", "")?.ifBlank { videoObj.optString("dynamic_cover", "") } ?: ""
            val duration = videoObj?.optInt("duration", 0) ?: 0

            if (noWmRaw.isBlank()) noWmRaw = dataObj.optString("video_no_watermark", "")
            if (noWmRaw.isBlank()) noWmRaw = dataObj.optString("nwm_video_url", "")
            if (noWmRaw.isBlank()) noWmRaw = dataObj.optString("downloadAddr", "")
            if (wmRaw.isBlank()) wmRaw = dataObj.optString("wm_video_url", "")
            if (noWmRaw.isBlank()) noWmRaw = videoObj?.optString("downloadAddr", "") ?: ""
            if (noWmRaw.isBlank()) noWmRaw = videoObj?.optString("playAddr", "") ?: ""

            val authorObj = dataObj.optJSONObject("author")
            val authorName = authorObj?.optString("name", "")?.ifBlank { authorObj.optString("nickname", "") }?.ifBlank { "TikTok Creator" } ?: "TikTok Creator"
            val authorHandleRaw = authorObj?.optString("unique_id", "") ?: authorObj?.optString("uniqueId", "") ?: ""
            val authorHandle = if (authorHandleRaw.isNotBlank()) "@$authorHandleRaw" else "@tiktok_user"
            var authorAvatarRaw = authorObj?.optString("avatar", "") ?: ""

            val musicObj = dataObj.optJSONObject("music")
            var musicUrlRaw = musicObj?.optString("play_url", "") ?: musicObj?.optString("playUrl", "") ?: ""
            if (musicUrlRaw.isBlank()) musicUrlRaw = dataObj.optString("music_url", "")

            val statsObj = dataObj.optJSONObject("stats")
            val like = statsObj?.optLong("likeCount", 0) ?: statsObj?.optLong("diggCount", 0) ?: 0
            val comment = statsObj?.optLong("commentCount", 0) ?: 0
            val playCount = statsObj?.optLong("playCount", 0) ?: 0

            // normalize
            val noWm = normalizeUrl(noWmRaw) ?: ""
            val wm = normalizeUrl(wmRaw) ?: ""
            val cover = normalizeUrl(coverRaw) ?: ""
            val musicUrl = normalizeUrl(musicUrlRaw) ?: ""
            val authorAvatar = normalizeUrl(authorAvatarRaw) ?: authorAvatarRaw

            val photoList = mutableListOf<String>()
            if (dataObj.has("images")) {
                val arr = dataObj.optJSONArray("images")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        when (item) {
                            is JSONObject -> {
                                val u = normalizeUrl(item.optString("url", "")) ?: continue
                                photoList.add(u)
                            }
                            is String -> {
                                normalizeUrl(item)?.let { photoList.add(it) }
                            }
                        }
                    }
                }
            }

            val filteredPhotos = normalizeUrlList(photoList)

            val mediaType = when {
                filteredPhotos.isNotEmpty() -> MediaType.PHOTO_CAROUSEL
                noWm.isNotBlank() || wm.isNotBlank() -> MediaType.VIDEO
                else -> MediaType.VIDEO
            }

            val finalNoWm = noWm.ifBlank { wm }
            val finalWm = wm.ifBlank { noWm }

            if (finalNoWm.isBlank() && filteredPhotos.isEmpty() && musicUrl.isBlank()) return null

            return TikTokMediaResult(
                id = id,
                title = title,
                authorName = authorName,
                authorHandle = authorHandle,
                authorAvatarUrl = authorAvatar,
                coverUrl = cover.ifBlank { authorAvatar },
                videoNoWmUrl = finalNoWm.takeIf { it.isNotBlank() },
                videoHdNoWmUrl = finalNoWm.takeIf { it.isNotBlank() },
                videoWmUrl = finalWm.takeIf { it.isNotBlank() },
                audioMp3Url = musicUrl.takeIf { it.isNotBlank() },
                photoUrls = filteredPhotos,
                mediaType = mediaType,
                durationSec = duration,
                playCount = playCount,
                likeCount = like,
                commentCount = comment
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun extractViaWebCoreEngine(url: String): TikTokMediaResult? {
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
                .header("Referer", "https://www.tiktok.com/")
                .timeout(15000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .get()

            val html = doc.html()

            val sigiStateScript = doc.select("script#SIGI_STATE").firstOrNull()?.data()
                ?: doc.select("script[id=SIGI_STATE]").firstOrNull()?.data()
                ?: ""

            val workingHtml = if (sigiStateScript.isNotBlank()) sigiStateScript else html

            var playAddrRaw = extractJsonUrlRaw(workingHtml, listOf("playAddr", "downloadAddr", "playUrl", "downloadUrl"))
            var coverRaw = extractJsonUrlRaw(workingHtml, listOf("dynamicCover", "originCover", "cover", "thumbnailUrl")) ?: ""
            if (coverRaw.isBlank()) {
                coverRaw = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
            }

            val authorName = extractJsonField(workingHtml, listOf("nickname", "authorName")) ?: "TikTok Creator"
            val uniqueId = extractJsonField(workingHtml, listOf("uniqueId", "unique_id")) ?: "tiktok_user"
            var authorAvatarRaw = extractJsonUrlRaw(workingHtml, listOf("avatarLarger", "avatarMedium", "avatarThumb", "avatar")) ?: ""

            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:description]")?.attr("content")
                ?: doc.title().ifBlank { "TikTok Video by HyTik" }

            val idMatch = Regex("""/video/(\d+)""").find(url) ?: Regex("""/photo/(\d+)""").find(url) ?: Regex(""""id":"(\d{10,})"""").find(workingHtml)
            val videoId = idMatch?.groupValues?.get(1) ?: System.currentTimeMillis().toString()

            val photoUrlsRaw = mutableListOf<String>()
            if (html.contains("imagePost") || html.contains("photo")) {
                val urlPattern = Regex("""https?://[^"']+\.(?:jpg|jpeg|png)[^"']*""")
                val matches = urlPattern.findAll(html).map { it.value }.filter {
                    it.contains("tiktok") || it.contains("muscdn") || it.contains("tiktokcdn")
                }.distinct().take(35).toList()
                photoUrlsRaw.addAll(matches)
            }

            val playAddr = normalizeUrl(playAddrRaw)
            val coverUrl = normalizeUrl(coverRaw) ?: ""
            val authorAvatar = normalizeUrl(authorAvatarRaw) ?: authorAvatarRaw
            val photoUrls = normalizeUrlList(photoUrlsRaw)

            if (playAddr.isNullOrBlank() && photoUrls.isEmpty()) {
                val ogVideoRaw = doc.selectFirst("meta[property=og:video]")?.attr("content") ?: ""
                val ogVideo = normalizeUrl(ogVideoRaw)
                if (!ogVideo.isNullOrBlank()) {
                    return TikTokMediaResult(
                        id = videoId,
                        title = title,
                        authorName = authorName,
                        authorHandle = "@$uniqueId",
                        authorAvatarUrl = authorAvatar,
                        coverUrl = coverUrl.ifBlank { ogVideo },
                        videoNoWmUrl = ogVideo,
                        videoHdNoWmUrl = ogVideo,
                        videoWmUrl = ogVideo,
                        audioMp3Url = null,
                        photoUrls = emptyList(),
                        mediaType = MediaType.VIDEO
                    )
                }
                return null
            }

            if (photoUrls.isNotEmpty() && playAddr.isNullOrBlank()) {
                return TikTokMediaResult(
                    id = videoId,
                    title = title,
                    authorName = authorName,
                    authorHandle = "@$uniqueId",
                    authorAvatarUrl = authorAvatar,
                    coverUrl = photoUrls.firstOrNull() ?: coverUrl,
                    videoNoWmUrl = null,
                    videoHdNoWmUrl = null,
                    videoWmUrl = null,
                    audioMp3Url = null,
                    photoUrls = photoUrls,
                    mediaType = MediaType.PHOTO_CAROUSEL
                )
            }

            if (!playAddr.isNullOrBlank()) {
                return TikTokMediaResult(
                    id = videoId,
                    title = title,
                    authorName = authorName,
                    authorHandle = "@$uniqueId",
                    authorAvatarUrl = authorAvatar,
                    coverUrl = coverUrl,
                    videoNoWmUrl = playAddr,
                    videoHdNoWmUrl = playAddr,
                    videoWmUrl = playAddr,
                    audioMp3Url = null,
                    photoUrls = photoUrls,
                    mediaType = if (photoUrls.isNotEmpty()) MediaType.PHOTO_CAROUSEL else MediaType.VIDEO
                )
            }
            return null
        } catch (_: Exception) {
            return null
        }
    }

    private fun extractJsonUrlRaw(source: String, keys: List<String>): String? {
        for (k in keys) {
            val rgx = Regex(""""$k"\s*:\s*"([^"]+)"""")
            val match = rgx.find(source)
            if (match != null) {
                var url = match.groupValues[1]
                url = unescapeJsonString(url)
                // kembalikan raw nanti dinormalisasi di luar
                return url
            }
        }
        return null
    }

    private fun extractJsonField(source: String, keys: List<String>): String? {
        for (k in keys) {
            val rgx = Regex(""""$k"\s*:\s*"([^"]+)"""")
            val match = rgx.find(source)
            if (match != null) {
                var v = match.groupValues[1]
                v = unescapeJsonString(v)
                if (v.isNotBlank()) return v
            }
        }
        return null
    }

    private fun unescapeJsonString(s: String): String {
        return s.replace("\\u002F", "/")
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003D", "=")
            .replace("\\u0025", "%")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun extractViaAlternateApis(url: String): TikTokMediaResult? {
        try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val req = Request.Builder()
                .url("https://tikmate.app/api/lookup?url=$encoded")
                .get()
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.contains("token") || body.contains("id")) {
                        val token = JSONObject(body).optString("token", "")
                        if (token.isNotBlank()) {
                            // future implementation
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
