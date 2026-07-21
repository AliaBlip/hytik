package com.aliablip.hytik.data.api.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class MediaType {
    VIDEO,
    PHOTO_CAROUSEL,
    AUDIO_ONLY
}

@Parcelize
data class TikTokMediaResult(
    val id: String,
    val title: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String,
    val coverUrl: String,
    val videoNoWmUrl: String? = null,
    val videoHdNoWmUrl: String? = null,
    val videoWmUrl: String? = null,
    val audioMp3Url: String? = null,
    val photoUrls: List<String> = emptyList(),
    val mediaType: MediaType = MediaType.VIDEO,
    val durationSec: Int = 0,
    val playCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0
) : Parcelable {

    fun getBestVideoUrl(): String? {
        return videoHdNoWmUrl?.takeIf { it.isNotBlank() }
            ?: videoNoWmUrl?.takeIf { it.isNotBlank() }
            ?: videoWmUrl?.takeIf { it.isNotBlank() }
    }

    fun getHdUrl(): String? = videoHdNoWmUrl?.takeIf { it.isNotBlank() } ?: getBestVideoUrl()
    
    fun getSdUrl(): String? = videoNoWmUrl?.takeIf { it.isNotBlank() } ?: getBestVideoUrl()

    fun getWmUrl(): String? = videoWmUrl?.takeIf { it.isNotBlank() } ?: videoNoWmUrl ?: videoHdNoWmUrl

    fun hasVideo(): Boolean = !getBestVideoUrl().isNullOrBlank()

    fun hasPhotos(): Boolean = photoUrls.isNotEmpty()

    fun getDisplayStats(): String = buildString {
        if (likeCount > 0) append("${formatCount(likeCount)} Suka")
        if (commentCount > 0) {
            if (isNotEmpty()) append(" • ")
            append("${formatCount(commentCount)} Komentar")
        }
        if (playCount > 0) {
            if (isNotEmpty()) append(" • ")
            append("${formatCount(playCount)} Tayangan")
        }
        if (durationSec > 0) {
            if (isNotEmpty()) append(" • ")
            append("${durationSec}s")
        }
        if (isEmpty()) append("Media TikTok")
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
