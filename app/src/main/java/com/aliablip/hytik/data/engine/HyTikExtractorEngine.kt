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

/**
 * HyTik Professional Extraction Engines
 * User tidak perlu tau kita pakai API eksternal, semua di-branding sebagai HyTik Core Engines
 */
enum class EngineMode {
    /** Smart Auto - Paling stabil, otomatis pilih jalur terbaik */
    SMART_AUTO,

    /** Ultra HD - Kualitas maksimum hingga 4K tanpa watermark */
    ULTRA_HD,

    /** Turbo Fast - Jalur super cepat untuk hasil instan */
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
        // Migration helper from legacy names (HYBRID_AUTO, TIKWM_HD, TIKLY_FAST)
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
                        // Ultra fallback to smart
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

    // ---------- URL Cleaning & Redirect Resolver ----------

    private fun extractAndCleanUrl(text: String): String? {
        if (text.isBlank()) return null
        // cari semua url tiktok dari text panjang
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
                // bersihkan trailing punctuation umum
                url = url.trimEnd('.', ',', '!', ')', ']', '}', '"', '\'')
                return url
            }
        }
        // fallback generic
        val generic = Regex("""https?://[^\s]+\.tiktok\.com/[^\s]+""", RegexOption.IGNORE_CASE).find(text)
        return generic?.value?.trim()?.trimEnd('.', ',', ')')
    }

    private fun resolveShortLink(originalUrl: String): String {
        val lower = originalUrl.lowercase()
        val needResolve = lower.contains("vt.tiktok.com") || lower.contains("vm.tiktok.com") || lower.contains("t.tiktok.com") || lower.contains("/t/")
        if (!needResolve) return originalUrl

        // Coba 2 metode: GET follow redirect + JSoup meta canonical
        try {
            val request = Request.Builder()
                .url(originalUrl)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0 Mobile Safari/537.36 TikTok 32.1.3")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl.isNotBlank() && finalUrl != originalUrl && finalUrl.contains("tiktok.com")) {
                    return finalUrl
                }
                // coba parse body untuk canonical jika server response html
                try {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.contains("tiktok.com")) {
                        val canonical = Regex("""<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(bodyStr)?.groupValues?.get(1)
                        if (!canonical.isNullOrBlank() && canonical.contains("tiktok.com")) return canonical
                        val ogUrl = Regex("""<meta[^>]+property=["']og:url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(bodyStr)?.groupValues?.get(1)
                        if (!ogUrl.isNullOrBlank() && ogUrl.contains("tiktok.com")) return ogUrl
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        }

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
            // final url after jsoup redirect
            val loc = doc.location()
            if (loc.isNotBlank() && loc != originalUrl && loc.contains("tiktok.com")) return loc
        } catch (_: Exception) {}

        return originalUrl
    }

    // ---------- SMART HYBRID (try all) ----------

    private fun extractViaSmartHybrid(url: String): TikTokMediaResult? {
        // 1. Ultra HD
        try {
            val ultra = extractViaUltraEngineInternal(url)
            if (ultra != null && ultra.hasPlayableContent()) return ultra
        } catch (_: Exception) {}

        // 2. Turbo Fast
        try {
            val turbo = extractViaTurboEngineInternal(url)
            if (turbo != null && turbo.hasPlayableContent()) return turbo
        } catch (_: Exception) {}

        // 3. WebCore Scraper
        try {
            val web = extractViaWebCoreEngine(url)
            if (web != null && web.hasPlayableContent()) return web
        } catch (_: Exception) {}

        // 4. TikMate / Alternate public APIs
        try {
            val alt = extractViaAlternateApis(url)
            if (alt != null && alt.hasPlayableContent()) return alt
        } catch (_: Exception) {}

        return null
    }

    // ---------- ULTRA HD ENGINE (Previously TikWM) ----------

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
            } catch (_: Exception) {
                // lanjut endpoint berikutnya
            }
        }
        return null
    }

    private fun parseTikWmJson(jsonStr: String): TikTokMediaResult? {
        try {
            val root = JSONObject(jsonStr)
            // support both {code:0, data:{}} and {data:{}} etc
            val code = root.optInt("code", 0)
            // allow code 0 or no code
            if (root.has("code") && code != 0) {
                // some APIs return code -1 with msg, treat as fail
                if (code != 0) {
                    // coba lihat apakah tetap ada data
                    if (!root.has("data")) return null
                }
            }
            val dataObj = when {
                root.has("data") && root.opt("data") is JSONObject -> root.getJSONObject("data")
                root.has("data") && root.opt("data") is JSONObject -> root.getJSONObject("data")
                root.has("data") -> root.optJSONObject("data")
                else -> root // fallback direct
            } ?: return null

            // Bisa juga data berisi di dalam "data" lagi? untuk feed/search
            val realData = if (dataObj.has("data") && dataObj.opt("data") is JSONObject) dataObj.getJSONObject("data") else dataObj

            val id = realData.optString("id", System.currentTimeMillis().toString())
            val title = realData.optString("title", "").takeIf { it.isNotBlank() } ?: "TikTok Video by HyTik"

            val cover = realData.optString("origin_cover", "").ifBlank { realData.optString("cover", "") }.ifBlank { realData.optString("ai_dynamic_cover", "") }
            val duration = realData.optInt("duration", 0)

            var play = realData.optString("play", "")
            var wmplay = realData.optString("wmplay", "")
            var hdplay = realData.optString("hdplay", "")
            // fallback alternate keys
            if (play.isBlank()) play = realData.optString("hdplay", "")
            if (play.isBlank()) play = realData.optString("playAddr", "")
            // some responses have "hdplay" = video no wm hd

            val musicUrl = realData.optString("music", "").ifBlank {
                val musicInfo = realData.optJSONObject("music_info")
                musicInfo?.optString("play", "") ?: ""
            }

            val authorObj = realData.optJSONObject("author")
            val authorName = authorObj?.optString("nickname", "")?.ifBlank { authorObj.optString("unique_id", "") } ?: "TikTok Creator"
            val authorHandle = authorObj?.optString("unique_id", "")?.let { if (it.isNotBlank()) "@$it" else "@tiktok_user" } ?: "@tiktok_user"
            val authorAvatar = authorObj?.optString("avatar", "") ?: ""

            val playCount = realData.optLong("play_count", 0)
            val diggCount = realData.optLong("digg_count", 0)
            val commentCount = realData.optLong("comment_count", 0)

            // images - bisa array string atau object
            val imagesArray = mutableListOf<String>()
            if (realData.has("images")) {
                val imgVal = realData.opt("images")
                if (imgVal is org.json.JSONArray) {
                    for (i in 0 until imgVal.length()) {
                        val u = imgVal.optString(i, "")
                        if (u.isNotBlank()) imagesArray.add(u)
                    }
                } else if (imgVal is String && imgVal.isNotBlank()) {
                    imagesArray.add(imgVal)
                }
            }
            // alternative: "image_post_info" etc
            if (imagesArray.isEmpty() && realData.has("images") ) {
                try {
                    val arr = realData.getJSONArray("images")
                    for (i in 0 until arr.length()) imagesArray.add(arr.getString(i))
                } catch (_: Exception) {}
            }

            val mediaType = when {
                imagesArray.isNotEmpty() -> MediaType.PHOTO_CAROUSEL
                play.isNotBlank() || hdplay.isNotBlank() || wmplay.isNotBlank() -> MediaType.VIDEO
                musicUrl.isNotBlank() -> MediaType.AUDIO_ONLY
                else -> MediaType.VIDEO
            }

            // pastikan minimal ada 1 video url
            // jika hd kosong, duplicate dari play
            val finalHd = hdplay.ifBlank { play }.ifBlank { wmplay }
            val finalNoWm = play.ifBlank { hdplay }.ifBlank { wmplay }
            val finalWm = wmplay.ifBlank { play }.ifBlank { hdplay }

            if (finalHd.isBlank() && finalNoWm.isBlank() && finalWm.isBlank() && imagesArray.isEmpty() && musicUrl.isBlank()) {
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
                photoUrls = imagesArray,
                mediaType = mediaType,
                durationSec = duration,
                playCount = playCount,
                likeCount = diggCount,
                commentCount = commentCount
            )
        } catch (_: Exception) {
            return null
        }
    }

    // ---------- TURBO FAST ENGINE (Previously TikLyDown) ----------

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
            } catch (_: Exception) {
                // lanjut endpoint berikutnya
            }
        }
        return null
    }

    private fun parseTikLyJson(jsonStr: String): TikTokMediaResult? {
        try {
            val root = JSONObject(jsonStr)
            // support wrapper {result:{}} or direct
            val dataObj = when {
                root.has("result") && root.opt("result") is JSONObject -> root.getJSONObject("result")
                root.has("data") && root.opt("data") is JSONObject -> root.getJSONObject("data")
                else -> root
            }

            val id = dataObj.optString("id", System.currentTimeMillis().toString())
            val title = dataObj.optString("title", "").ifBlank { dataObj.optString("desc", "") }.ifBlank { "TikTok Video by HyTik" }

            // video
            val videoObj = dataObj.optJSONObject("video")
            var noWm = videoObj?.optString("noWatermark", "") ?: ""
            var wm = videoObj?.optString("watermark", "") ?: ""
            val cover = videoObj?.optString("cover", "")?.ifBlank { videoObj.optString("dynamic_cover", "") } ?: ""
            val duration = videoObj?.optInt("duration", 0) ?: 0

            // fallback other keys (video1, video2, hd, etc)
            if (noWm.isBlank()) noWm = dataObj.optString("video_no_watermark", "")
            if (noWm.isBlank()) noWm = dataObj.optString("nwm_video_url", "")
            if (noWm.isBlank()) noWm = dataObj.optString("downloadAddr", "")
            if (wm.isBlank()) wm = dataObj.optString("wm_video_url", "")
            if (noWm.isBlank()) noWm = videoObj?.optString("downloadAddr", "") ?: ""
            if (noWm.isBlank()) noWm = videoObj?.optString("playAddr", "") ?: ""

            // author
            val authorObj = dataObj.optJSONObject("author")
            val authorName = authorObj?.optString("name", "")?.ifBlank { authorObj.optString("nickname", "") }?.ifBlank { "TikTok Creator" } ?: "TikTok Creator"
            val authorHandleRaw = authorObj?.optString("unique_id", "") ?: authorObj?.optString("uniqueId", "") ?: ""
            val authorHandle = if (authorHandleRaw.isNotBlank()) "@$authorHandleRaw" else "@tiktok_user"
            val authorAvatar = authorObj?.optString("avatar", "") ?: ""

            // music
            val musicObj = dataObj.optJSONObject("music")
            var musicUrl = musicObj?.optString("play_url", "") ?: musicObj?.optString("playUrl", "") ?: ""
            if (musicUrl.isBlank()) musicUrl = dataObj.optString("music_url", "")

            // stats
            val statsObj = dataObj.optJSONObject("stats")
            val like = statsObj?.optLong("likeCount", 0) ?: statsObj?.optLong("diggCount", 0) ?: 0
            val comment = statsObj?.optLong("commentCount", 0) ?: 0
            val playCount = statsObj?.optLong("playCount", 0) ?: 0

            // images
            val photoList = mutableListOf<String>()
            if (dataObj.has("images")) {
                val arr = dataObj.optJSONArray("images")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        when (item) {
                            is JSONObject -> {
                                val u = item.optString("url", "")
                                if (u.isNotBlank()) photoList.add(u)
                            }
                            is String -> if (item.isNotBlank()) photoList.add(item)
                        }
                    }
                } else {
                    // may be object
                    val obj = dataObj.optJSONObject("images")
                    // ignore
                }
            }

            // jika tidak ada video tapi ada photo, media type photo
            val mediaType = when {
                photoList.isNotEmpty() -> MediaType.PHOTO_CAROUSEL
                noWm.isNotBlank() || wm.isNotBlank() -> MediaType.VIDEO
                else -> MediaType.VIDEO
            }

            val finalNoWm = noWm.ifBlank { wm }
            val finalWm = wm.ifBlank { noWm }

            if (finalNoWm.isBlank() && photoList.isEmpty() && musicUrl.isBlank()) return null

            return TikTokMediaResult(
                id = id,
                title = title,
                authorName = authorName,
                authorHandle = authorHandle,
                authorAvatarUrl = authorAvatar,
                coverUrl = cover.ifBlank { authorAvatar },
                videoNoWmUrl = finalNoWm.takeIf { it.isNotBlank() },
                videoHdNoWmUrl = finalNoWm.takeIf { it.isNotBlank() }, // turbo provides same HD
                videoWmUrl = finalWm.takeIf { it.isNotBlank() },
                audioMp3Url = musicUrl.takeIf { it.isNotBlank() },
                photoUrls = photoList,
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

    // ---------- WEB CORE ENGINE (Scraper) ----------

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

            // 1. Try SIGI_STATE
            val sigiStateScript = doc.select("script#SIGI_STATE").firstOrNull()?.data()
                ?: doc.select("script[id=SIGI_STATE]").firstOrNull()?.data()
                ?: ""

            var workingHtml = if (sigiStateScript.isNotBlank()) sigiStateScript else html

            // Extract video URLs via regex
            val playAddr = extractJsonUrl(workingHtml, listOf("playAddr", "downloadAddr", "playUrl", "downloadUrl"))
            var coverUrl = extractJsonUrl(workingHtml, listOf("dynamicCover", "originCover", "cover", "thumbnailUrl")) ?: ""
            if (coverUrl.isBlank()) {
                coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
            }

            var authorName = extractJsonField(workingHtml, listOf("nickname", "authorName")) ?: "TikTok Creator"
            val uniqueId = extractJsonField(workingHtml, listOf("uniqueId", "unique_id")) ?: "tiktok_user"
            val authorAvatar = extractJsonUrl(workingHtml, listOf("avatarLarger", "avatarMedium", "avatarThumb", "avatar")) ?: ""

            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:description]")?.attr("content")
                ?: doc.title().ifBlank { "TikTok Video by HyTik" }

            // id
            val idMatch = Regex("""/video/(\d+)""").find(url) ?: Regex("""/photo/(\d+)""").find(url) ?: Regex(""""id":"(\d{10,})"""").find(workingHtml)
            val videoId = idMatch?.groupValues?.get(1) ?: System.currentTimeMillis().toString()

            // cek photo carousel di halaman
            val photoUrls = mutableListOf<String>()
            // pola untuk foto tiktok slide: "imagePost": {"images": [{"imageURL":...}]}
            val imageRegex = Regex(""""imageURL"\s*:\s*\{\s*"urlList"\s*:\s*\[([^\]]+)\]""")
            // simpler: cari semua https url dengan .jpeg/.jpg yang mengandung tiktok
            if (html.contains("imagePost") || html.contains("photo")) {
                val urlPattern = Regex("""https?://[^"']+\.(?:jpg|jpeg|png)[^"']*""")
                val matches = urlPattern.findAll(html).map { it.value }.filter {
                    it.contains("tiktok") || it.contains("muscdn") || it.contains("tiktokcdn")
                }.distinct().take(35).toList()
                // filter valid
                photoUrls.addAll(matches)
            }

            if (playAddr.isNullOrBlank() && photoUrls.isEmpty()) {
                // fallback og:video
                val ogVideo = doc.selectFirst("meta[property=og:video]")?.attr("content")
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

    private fun extractJsonUrl(source: String, keys: List<String>): String? {
        for (k in keys) {
            // pattern "key":"url"
            val rgx = Regex(""""$k"\s*:\s*"([^"]+)"""")
            val match = rgx.find(source)
            if (match != null) {
                var url = match.groupValues[1]
                url = unescapeJsonString(url)
                if (url.startsWith("http")) return url
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

    // ---------- ALTERNATE APIS (third fallback) ----------

    private fun extractViaAlternateApis(url: String): TikTokMediaResult? {
        // Try SnapTik-ish endpoint via Rapid? Or public https://tikmate.app/api
        try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            // try tikmate
            val req = Request.Builder()
                .url("https://tikmate.app/api/lookup?url=$encoded")
                .get()
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.contains("token") || body.contains("id")) {
                        // parse token then attempt second? For simplicity skip complex two-step
                        val token = JSONObject(body).optString("token", "")
                        if (token.isNotBlank()) {
                            // Could fetch video but need deeper, return null for now
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        // else return null; hybrid will fail after this
        return null
    }
}
