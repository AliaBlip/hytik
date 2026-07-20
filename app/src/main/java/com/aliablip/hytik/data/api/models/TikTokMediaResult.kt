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
            ?: videoWmUrl
    }
}
