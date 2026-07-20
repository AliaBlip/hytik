package com.aliablip.hytik.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.aliablip.hytik.data.db.DownloadHistoryEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun formatSizeKb(sizeKb: Long): String {
        return if (sizeKb >= 1024) {
            String.format(Locale.US, "%.1f MB", sizeKb / 1024.0)
        } else {
            "$sizeKb KB"
        }
    }

    fun formatDate(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        return sdf.format(Date(timestampMillis))
    }

    fun openFile(context: Context, item: DownloadHistoryEntity) {
        try {
            val uri = getUriForEntity(context, item)
            val mimeType = when (item.mediaType) {
                "VIDEO" -> "video/*"
                "AUDIO" -> "audio/*"
                "PHOTO" -> "image/*"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            UiUtils.showToast(context, "Tidak ada aplikasi untuk membuka file ini atau file telah dipindahkan.")
        }
    }

    fun shareFile(context: Context, item: DownloadHistoryEntity) {
        try {
            val uri = getUriForEntity(context, item)
            val mimeType = when (item.mediaType) {
                "VIDEO" -> "video/*"
                "AUDIO" -> "audio/*"
                "PHOTO" -> "image/*"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, item.title)
                putExtra(Intent.EXTRA_TEXT, "Diunduh dengan HyTik - TikTok Downloader: ${item.title}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan via HyTik"))
        } catch (e: Exception) {
            UiUtils.showToast(context, "Gagal membagikan file: ${e.message}")
        }
    }

    private fun getUriForEntity(context: Context, item: DownloadHistoryEntity): Uri {
        if (item.fileUriString.isNotBlank() && item.fileUriString.startsWith("content://")) {
            return Uri.parse(item.fileUriString)
        }
        val file = File(item.filePath)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }
    }
}
