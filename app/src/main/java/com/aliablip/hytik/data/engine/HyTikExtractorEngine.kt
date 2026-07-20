package com.aliablip.hytik.data.engine

import com.aliablip.hytik.data.api.ApiClient
import com.aliablip.hytik.data.api.models.MediaType
import com.aliablip.hytik.data.api.models.TikLyResponse
import com.aliablip.hytik.data.api.models.TikWmResponse
import com.aliablip.hytik.data.api.models.TikTokMediaResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

enum class EngineMode {
    HYBRID_AUTO,
    TIKWM_HD,
    TIKLY_FAST
}

class HyTikExtractorEngine {

    private val apiService = ApiClient.apiService
    private val client = ApiClient.okHttpClient

    suspend fun extractMedia(inputUrl: String, mode: EngineMode = EngineMode.HYBRID_AUTO): Result<TikTokMediaResult> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = extractAndCleanUrl(inputUrl)
                    ?: return@withContext Result.failure(IllegalArgumentException("Tautan TikTok tidak valid! Pastikan link mengandung tiktok.com atau vt.tiktok.com"))

                val resolvedUrl = resolveRedirectsIfNeeded(cleanUrl)

                when (mode) {
                    EngineMode.TIKWM_HD -> {
                        val result = extractViaTikWm(resolvedUrl)
                        if (result != null) Result.success(result)
                        else Result.failure(Exception("Gagal mengambil data melalui Engine 1 (TikWM)."))
                    }
                    EngineMode.TIKLY_FAST -> {
                        val result = extractViaTikLy(resolvedUrl)
                        if (result != null) Result.success(result)
                        else Result.failure(Exception("Gagal mengambil data melalui Engine 2 (TikLyDown)."))
                    }
                    EngineMode.HYBRID_AUTO -> {
                        // Priority 1: TikWM HD Pro
                        try {
                            val tikWm = extractViaTikWm(resolvedUrl)
                            if (tikWm != null && (tikWm.videoHdNoWmUrl != null || tikWm.videoNoWmUrl != null || tikWm.photoUrls.isNotEmpty())) {
                                return@withContext Result.success(tikWm)
                            }
                        } catch (e: Exception) {
                            // Continue to Engine 2 fallback
                        }

                        // Priority 2: TikLyDown Fast
                        try {
                            val tikLy = extractViaTikLy(resolvedUrl)
                            if (tikLy != null && (tikLy.videoNoWmUrl != null || tikLy.photoUrls.isNotEmpty())) {
                                return@withContext Result.success(tikLy)
                            }
                        } catch (e: Exception) {
                            // Continue to Engine 3 fallback
                        }

                        // Priority 3: Direct oEmbed + Scraper fallback
                        val oembedResult = extractViaOembedAndScraper(resolvedUrl)
                        if (oembedResult != null) {
                            Result.success(oembedResult)
                        } else {
                            Result.failure(Exception("Semua Engine ekstraksi gagal memuat tautan ini. Pastikan akun TikTok tidak diprivat dan tautan masih aktif."))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun extractAndCleanUrl(text: String): String? {
        val regex = Regex("https?://(?:www\\.|vt\\.|vm\\.|m\\.|t\\.)?tiktok\\.com/[^\\s\"')<>]+")
        val match = regex.find(text)
        return match?.value?.trim()
    }

    private fun resolveRedirectsIfNeeded(url: String): String {
        val lower = url.lowercase()
        if (lower.contains("vt.tiktok.com") || lower.contains("vm.tiktok.com") || lower.contains("t.tiktok.com") || lower.contains("/t/")) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()
                client.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    if (finalUrl.isNotBlank() && finalUrl != url) {
                        return finalUrl
                    }
                }
            } catch (e: Exception) {
                // Ignore redirection failure, return original url
            }
        }
        return url
    }

    private suspend fun extractViaTikWm(url: String): TikTokMediaResult? {
        val response = apiService.fetchTikWm(url, hd = 1)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.code == 0 && body.data != null) {
                val data = body.data
                val photoList = data.images?.filter { it.isNotBlank() } ?: emptyList()
                val mediaType = when {
                    photoList.isNotEmpty() -> MediaType.PHOTO_CAROUSEL
                    !data.hdPlayUrl.isNullOrBlank() || !data.playUrl.isNullOrBlank() -> MediaType.VIDEO
                    else -> MediaType.AUDIO_ONLY
                }

                val authorName = data.author?.nickname ?: "TikTok Creator"
                val authorHandle = data.author?.uniqueId?.let { "@$it" } ?: "@user"
                val authorAvatar = data.author?.avatar ?: ""
                val coverUrl = data.originCover?.takeIf { it.isNotBlank() } ?: data.cover ?: ""

                return TikTokMediaResult(
                    id = data.id ?: System.currentTimeMillis().toString(),
                    title = data.title?.takeIf { it.isNotBlank() } ?: "TikTok Media",
                    authorName = authorName,
                    authorHandle = authorHandle,
                    authorAvatarUrl = authorAvatar,
                    coverUrl = coverUrl,
                    videoNoWmUrl = data.playUrl,
                    videoHdNoWmUrl = data.hdPlayUrl?.takeIf { it.isNotBlank() } ?: data.playUrl,
                    videoWmUrl = data.wmPlayUrl,
                    audioMp3Url = data.musicUrl ?: data.musicInfo?.playUrl,
                    photoUrls = photoList,
                    mediaType = mediaType,
                    durationSec = data.duration,
                    playCount = data.playCount,
                    likeCount = data.diggCount,
                    commentCount = data.commentCount
                )
            }
        }
        return null
    }

    private suspend fun extractViaTikLy(url: String): TikTokMediaResult? {
        val response = apiService.fetchTikLyDown(url)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && (body.video != null || body.images != null)) {
                val photoList = body.images?.mapNotNull { it.url } ?: emptyList()
                val mediaType = when {
                    photoList.isNotEmpty() -> MediaType.PHOTO_CAROUSEL
                    body.video?.noWatermark != null -> MediaType.VIDEO
                    else -> MediaType.AUDIO_ONLY
                }

                val authorName = body.author?.name ?: "TikTok Creator"
                val authorHandle = body.author?.uniqueId?.let { "@$it" } ?: "@user"
                val authorAvatar = body.author?.avatar ?: ""
                val coverUrl = body.video?.cover ?: body.video?.dynamicCover ?: ""

                return TikTokMediaResult(
                    id = body.id ?: System.currentTimeMillis().toString(),
                    title = body.title?.takeIf { it.isNotBlank() } ?: "TikTok Video",
                    authorName = authorName,
                    authorHandle = authorHandle,
                    authorAvatarUrl = authorAvatar,
                    coverUrl = coverUrl,
                    videoNoWmUrl = body.video?.noWatermark,
                    videoHdNoWmUrl = body.video?.noWatermark,
                    videoWmUrl = body.video?.watermark,
                    audioMp3Url = body.music?.playUrl,
                    photoUrls = photoList,
                    mediaType = mediaType,
                    durationSec = body.video?.duration ?: 0,
                    playCount = body.stats?.playCount ?: 0,
                    likeCount = body.stats?.likeCount ?: 0,
                    commentCount = body.stats?.commentCount ?: 0
                )
            }
        }
        return null
    }

    private fun extractViaOembedAndScraper(url: String): TikTokMediaResult? {
        try {
            val oembedUrl = "https://www.tiktok.com/oembed?url=$url"
            val doc = Jsoup.connect(oembedUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .ignoreContentType(true)
                .get()

            val jsonText = doc.text()
            if (jsonText.contains("\"title\"")) {
                val titleMatch = Regex("\"title\":\"(.*?)\"").find(jsonText)?.groupValues?.get(1) ?: "TikTok Post"
                val authorMatch = Regex("\"author_name\":\"(.*?)\"").find(jsonText)?.groupValues?.get(1) ?: "TikTok Creator"
                val thumbMatch = Regex("\"thumbnail_url\":\"(.*?)\"").find(jsonText)?.groupValues?.get(1)?.replace("\\/", "/") ?: ""

                // Extract Video ID from URL
                val idMatch = Regex("/video/(\\d+)").find(url) ?: Regex("/photo/(\\d+)").find(url)
                val id = idMatch?.groupValues?.get(1) ?: System.currentTimeMillis().toString()

                return TikTokMediaResult(
                    id = id,
                    title = titleMatch.replace("\\u0026", "&"),
                    authorName = authorMatch,
                    authorHandle = "@$authorMatch",
                    authorAvatarUrl = thumbMatch,
                    coverUrl = thumbMatch,
                    videoNoWmUrl = null,
                    videoHdNoWmUrl = null,
                    videoWmUrl = null,
                    audioMp3Url = null,
                    photoUrls = emptyList(),
                    mediaType = MediaType.VIDEO
                )
            }
        } catch (e: Exception) {
            // Ignore oEmbed error
        }
        return null
    }
}
