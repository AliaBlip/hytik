package com.aliablip.hytik.data.api.models

import com.google.gson.annotations.SerializedName

data class TikLyResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("stats") val stats: TikLyStats? = null,
    @SerializedName("video") val video: TikLyVideo? = null,
    @SerializedName("music") val music: TikLyMusic? = null,
    @SerializedName("author") val author: TikLyAuthor? = null,
    @SerializedName("images") val images: List<TikLyImage>? = null
)

data class TikLyStats(
    @SerializedName("likeCount") val likeCount: Long = 0,
    @SerializedName("commentCount") val commentCount: Long = 0,
    @SerializedName("shareCount") val shareCount: Long = 0,
    @SerializedName("playCount") val playCount: Long = 0
)

data class TikLyVideo(
    @SerializedName("noWatermark") val noWatermark: String? = null,
    @SerializedName("watermark") val watermark: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("dynamic_cover") val dynamicCover: String? = null,
    @SerializedName("duration") val duration: Int = 0
)

data class TikLyMusic(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("play_url") val playUrl: String? = null,
    @SerializedName("author") val author: String? = null
)

data class TikLyAuthor(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("unique_id") val uniqueId: String? = null,
    @SerializedName("avatar") val avatar: String? = null
)

data class TikLyImage(
    @SerializedName("url") val url: String? = null
)
