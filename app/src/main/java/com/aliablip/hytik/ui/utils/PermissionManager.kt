package com.aliablip.hytik.ui.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * HyTik Professional Permission Manager
 * Menangani izin media/gambar secara profesional sesuai Android 13+ dan versi lama
 */
object PermissionManager {

    fun getRequiredMediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ granular media permissions + notification
            mutableListOf<String>().apply {
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            // Android 9 and below need write
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun getNotificationPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null
    }

    fun hasMediaPermissions(context: Context): Boolean {
        val perms = getRequiredMediaPermissions()
        // untuk notifikasi, kita treat optional tapi penting
        // check hanya media permissions yang wajib
        val mediaOnly = perms.filter { it != Manifest.permission.POST_NOTIFICATIONS }
        return mediaOnly.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val notif = getNotificationPermission() ?: return true // auto granted <33
        return ContextCompat.checkSelfPermission(context, notif) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllPermissions(context: Context): Boolean {
        return hasMediaPermissions(context) && hasNotificationPermission(context)
    }

    fun getMissingPermissions(context: Context): Array<String> {
        return getRequiredMediaPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // fallback
            val intent = Intent(Settings.ACTION_SETTINGS)
            context.startActivity(intent)
        }
    }

    fun getPermissionRationale(permission: String): String = when (permission) {
        Manifest.permission.READ_MEDIA_VIDEO -> "HyTik memerlukan akses Video untuk menampilkan dan mengelola video yang sudah didownload di galeri Anda."
        Manifest.permission.READ_MEDIA_IMAGES -> "HyTik memerlukan akses Gambar/Foto untuk menyimpan dan menampilkan carousel foto TikTok yang didownload."
        Manifest.permission.READ_MEDIA_AUDIO -> "HyTik memerlukan akses Musik/Audio untuk menyimpan file MP3 dari TikTok."
        Manifest.permission.READ_EXTERNAL_STORAGE -> "HyTik memerlukan akses Penyimpanan untuk menyimpan file video, foto, dan musik TikTok ke folder HyTik di perangkat Anda."
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Izin tulis penyimpanan diperlukan untuk Android versi lama agar dapat menyimpan file ke galeri."
        Manifest.permission.POST_NOTIFICATIONS -> "HyTik menampilkan notifikasi progress download di bar notifikasi agar Anda tahu status unduhan berlangsung dan selesai."
        else -> "Izin ini diperlukan agar HyTik dapat bekerja optimal."
    }
}
