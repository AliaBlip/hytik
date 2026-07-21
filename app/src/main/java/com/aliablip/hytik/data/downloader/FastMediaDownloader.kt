package com.aliablip.hytik.data.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.aliablip.hytik.data.api.ApiClient
import com.aliablip.hytik.data.db.AppDatabase
import com.aliablip.hytik.data.db.DownloadHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object FastMediaDownloader {

    suspend fun downloadMedia(
        context: Context,
        url: String,
        fileName: String,
        mediaType: String, // "VIDEO", "AUDIO", "PHOTO"
        title: String,
        authorName: String,
        coverUrl: String,
        onProgress: (percent: Int, downloadedKb: Long, totalKb: Long) -> Unit
    ): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                // gunakan downloadClient yang lebih optimal untuk file besar
                val client = ApiClient.downloadClient

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Referer", "https://www.tiktok.com/")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    return@withContext Result.failure(Exception("Server menolak unduhan (HTTP ${response.code}). Coba ganti mode engine ke Smart Auto."))
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                val mimeType = when (mediaType) {
                    "VIDEO" -> "video/mp4"
                    "AUDIO" -> "audio/mpeg"
                    "PHOTO" -> "image/jpeg"
                    else -> "application/octet-stream"
                }

                val targetDirName = when (mediaType) {
                    "VIDEO" -> Environment.DIRECTORY_MOVIES
                    "AUDIO" -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_PICTURES
                } + "/HyTik"

                var outputStream: OutputStream? = null
                var finalUri: Uri? = null
                var finalFilePath = ""

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetDirName)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val collectionUri = when (mediaType) {
                        "VIDEO" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "AUDIO" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }

                    val itemUri = resolver.insert(collectionUri, contentValues)
                        ?: return@withContext Result.failure(Exception("Gagal membuat entri di MediaStore. Pastikan izin media diberikan."))
                    finalUri = itemUri
                    outputStream = resolver.openOutputStream(itemUri)
                    finalFilePath = "$targetDirName/$fileName"

                    if (outputStream == null) {
                        return@withContext Result.failure(Exception("Gagal membuka aliran penyimpanan. Coba aktifkan izin media di pengaturan."))
                    }

                    val buffer = ByteArray(8192)
                    var bytesCopied: Long = 0
                    var read: Int
                    val totalKb = if (contentLength > 0) contentLength / 1024 else 0L

                    while (inputStream.read(buffer).also { read = it } >= 0) {
                        outputStream.write(buffer, 0, read)
                        bytesCopied += read
                        if (contentLength > 0) {
                            val percent = ((bytesCopied * 100) / contentLength).toInt().coerceIn(0, 100)
                            val downloadedKb = bytesCopied / 1024
                            onProgress(percent, downloadedKb, totalKb)
                        } else {
                            // indeterminate
                            val downloadedKb = bytesCopied / 1024
                            onProgress(0, downloadedKb, 0)
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    // mark as not pending
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)

                } else {
                    val publicDir = Environment.getExternalStoragePublicDirectory(
                        when (mediaType) {
                            "VIDEO" -> Environment.DIRECTORY_MOVIES
                            "AUDIO" -> Environment.DIRECTORY_MUSIC
                            else -> Environment.DIRECTORY_PICTURES
                        }
                    )
                    val hytikDir = File(publicDir, "HyTik")
                    if (!hytikDir.exists()) hytikDir.mkdirs()

                    val targetFile = File(hytikDir, fileName)
                    // jika sudah ada, overwrite
                    if (targetFile.exists()) targetFile.delete()

                    outputStream = FileOutputStream(targetFile)
                    finalFilePath = targetFile.absolutePath
                    finalUri = Uri.fromFile(targetFile)

                    val buffer = ByteArray(8192)
                    var bytesCopied: Long = 0
                    var read: Int
                    val totalKb = if (contentLength > 0) contentLength / 1024 else 0L

                    while (inputStream.read(buffer).also { read = it } >= 0) {
                        outputStream.write(buffer, 0, read)
                        bytesCopied += read
                        if (contentLength > 0) {
                            val percent = ((bytesCopied * 100) / contentLength).toInt().coerceIn(0, 100)
                            val downloadedKb = bytesCopied / 1024
                            onProgress(percent, downloadedKb, totalKb)
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                }

                // Save to History DB
                try {
                    val dao = AppDatabase.getInstance(context).downloadHistoryDao()
                    val entity = DownloadHistoryEntity(
                        id = System.currentTimeMillis().toString() + "_" + fileName.hashCode(),
                        title = title,
                        authorName = authorName,
                        coverUrl = coverUrl,
                        filePath = finalFilePath,
                        fileUriString = finalUri?.toString() ?: "",
                        mediaType = mediaType,
                        fileSizeKb = if (contentLength > 0) contentLength / 1024 else 0,
                        downloadedAtMillis = System.currentTimeMillis()
                    )
                    dao.insertHistory(entity)
                } catch (_: Exception) {
                    // history save fail shouldn't block success
                }

                Result.success(finalUri ?: Uri.EMPTY)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Gagal mengunduh file. Periksa koneksi internet."))
            }
        }
    }
}
