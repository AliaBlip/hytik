package com.aliablip.hytik.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authorName: String,
    val coverUrl: String,
    val filePath: String,
    val fileUriString: String,
    val mediaType: String, // "VIDEO", "AUDIO", "PHOTO"
    val fileSizeKb: Long,
    val downloadedAtMillis: Long
)
