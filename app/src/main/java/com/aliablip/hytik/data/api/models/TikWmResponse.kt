package com.aliablip.hytik.data.api.models

import com.google.gson.annotations.SerializedName

data class TikWmResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("data") val data: TikWmData? = null
)

data class TikWmData(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("origin_cover") val originCover: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("play") val playUrl: String? = null,
    @SerializedName("wmplay") val wmPlayUrl: String? = null,
    @SerializedName("hdplay") val hdPlayUrl: String? = null,
    @SerializedName("music") val musicUrl: String? = null,
    @SerializedName("music_info") val musicInfo: TikWmMusicInfo? = null,
    @SerializedName("play_count") val playCount: Long = 0,
    @SerializedName("digg_count") val diggCount: Long = 0,
    @SerializedName("comment_count") val commentCount: Long = 0,
    @SerializedName("share_count") val shareCount: Long = 0,
    @SerializedName("download_count") val downloadCount: Long = 0,
    @SerializedName("author") val author: TikWmAuthor? = null,
    @SerializedName("images") val images: List<String>? = null
)

data class TikWmAuthor(
    @SerializedName("id") val id: String? = null,
    @SerializedName("unique_id") val uniqueId: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("avatar") val avatar: String? = null
)

data class TikWmMusicInfo(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("play") val playUrl: String? = null,
    @SerializedName("author") val author: String? = null
)
