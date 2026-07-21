package com.aliablip.hytik.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.aliablip.hytik.R
import java.io.File

object DownloadNotificationHelper {

    const val CHANNEL_ID = "hytik_download_channel_v2"
    const val CHANNEL_NAME = "HyTik Downloads"
    const val CHANNEL_DESC = "Notifikasi progress dan hasil download video/foto/musik TikTok"

    private const val GROUP_KEY = "com.aliablip.hytik.DOWNLOADS"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                enableVibration(false)
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)

            // channel untuk completed (high importance)
            val completedChannel = NotificationChannel(
                "${CHANNEL_ID}_completed",
                "HyTik Download Selesai",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi ketika download selesai"
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(completedChannel)
        }
    }

    fun showProgressNotification(
        context: Context,
        notificationId: Int,
        title: String,
        fileName: String,
        progress: Int
    ) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Mengunduh: $title")
                .setContentText("$fileName • $progress%")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, progress == 0)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setGroup(GROUP_KEY)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)

            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            // notification permission not granted, ignore
        } catch (_: Exception) {}
    }

    fun showCompletedNotification(
        context: Context,
        notificationId: Int,
        title: String,
        fileUri: Uri?,
        mediaType: String
    ) {
        try {
            val completedChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "${CHANNEL_ID}_completed" else CHANNEL_ID

            val openIntent = fileUri?.let { uri ->
                Intent(Intent.ACTION_VIEW).apply {
                    val mime = when (mediaType) {
                        "VIDEO" -> "video/*"
                        "AUDIO" -> "audio/*"
                        "PHOTO" -> "image/*"
                        else -> "*/*"
                    }
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val pendingIntent = openIntent?.let {
                PendingIntent.getActivity(context, notificationId, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }

            val builder = NotificationCompat.Builder(context, completedChannelId)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle("Download Selesai")
                .setContentText(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title berhasil disimpan di folder HyTik"))
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_KEY)
                .setContentIntent(pendingIntent)

            // add open action if possible
            if (pendingIntent != null) {
                builder.addAction(R.drawable.ic_play, "Buka", pendingIntent)
            }

            // share action
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when (mediaType) {
                    "VIDEO" -> "video/*"
                    "AUDIO" -> "audio/*"
                    "PHOTO" -> "image/*"
                    else -> "*/*"
                }
                fileUri?.let { putExtra(Intent.EXTRA_STREAM, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val sharePending = PendingIntent.getActivity(
                context,
                notificationId + 10000,
                Intent.createChooser(shareIntent, "Bagikan via HyTik"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_share, "Bagikan", sharePending)

            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (_: Exception) {}
    }

    fun showFailedNotification(
        context: Context,
        notificationId: Int,
        title: String,
        error: String
    ) {
        try {
            val completedChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "${CHANNEL_ID}_completed" else CHANNEL_ID
            val builder = NotificationCompat.Builder(context, completedChannelId)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Download Gagal")
                .setContentText("$title: $error")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Gagal mengunduh $title\n$error"))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_KEY)

            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (_: Exception) {}
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {}
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
    }
}
