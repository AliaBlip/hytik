package com.aliablip.hytik

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.aliablip.hytik.data.notification.DownloadNotificationHelper
import com.aliablip.hytik.data.preferences.AppPreferences
import com.aliablip.hytik.databinding.ActivityMainBinding
import com.aliablip.hytik.ui.main.MainViewModel
import com.aliablip.hytik.ui.main.MainViewModelFactory
import com.aliablip.hytik.ui.tabs.DownloaderFragment
import com.aliablip.hytik.ui.tabs.HistoryFragment
import com.aliablip.hytik.ui.tabs.SettingsFragment
import com.aliablip.hytik.ui.utils.NetworkUtils
import com.aliablip.hytik.ui.utils.PermissionManager
import com.aliablip.hytik.ui.utils.UiUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory() }
    private lateinit var preferences: AppPreferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (!allGranted) {
            // optional prompt, not blocking
            val missing = PermissionManager.getMissingPermissions(this)
            if (missing.isNotEmpty() && !preferences.hasAskedMediaPermission) {
                preferences.hasAskedMediaPermission = true
                // show subtle info only first time
            }
        }
        // create channel anyway
        DownloadNotificationHelper.createChannel(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)

        setupNavigation()
        handleIncomingIntent(intent)

        // Professional: create notification channels early
        DownloadNotificationHelper.createChannel(this)

        // Professional: request media + notification permissions on first launch
        requestInitialPermissions()
    }

    private fun requestInitialPermissions() {
        val missing = PermissionManager.getMissingPermissions(this)
        if (missing.isNotEmpty()) {
            // Tampilkan dialog profesional singkat sebelum request
            if (!preferences.hasAskedMediaPermission) {
                AlertDialog.Builder(this)
                    .setTitle("Izin Profesional HyTik")
                    .setMessage("Agar HyTik bekerja optimal:\n\n• Akses Video/Gambar/Audio untuk menyimpan ke galeri\n• Notifikasi untuk progress download di bar status\n\nIzin ini membuat pengalaman download jadi profesional & transparan.")
                    .setPositiveButton("Aktifkan Izin") { _, _ ->
                        preferences.hasAskedMediaPermission = true
                        permissionLauncher.launch(missing)
                    }
                    .setNegativeButton("Nanti") { d, _ ->
                        preferences.hasAskedMediaPermission = true
                        d.dismiss()
                    }
                    .show()
            } else {
                // second time just launch silently
                permissionLauncher.launch(missing)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun setupNavigation() {
        val fragments = listOf(
            DownloaderFragment(),
            HistoryFragment(),
            SettingsFragment()
        )

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        binding.viewPager.isUserInputEnabled = false

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_downloader -> binding.viewPager.setCurrentItem(0, false)
                R.id.nav_history -> binding.viewPager.setCurrentItem(1, false)
                R.id.nav_settings -> binding.viewPager.setCurrentItem(2, false)
            }
            true
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> binding.bottomNav.selectedItemId = R.id.nav_downloader
                    1 -> binding.bottomNav.selectedItemId = R.id.nav_history
                    2 -> binding.bottomNav.selectedItemId = R.id.nav_settings
                }
            }
        })
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedText.isNotBlank() && NetworkUtils.containsTikTokUrl(sharedText)) {
                val extractedUrl = NetworkUtils.extractTikTokUrl(sharedText)
                if (extractedUrl != null) {
                    binding.viewPager.setCurrentItem(0, false)
                    binding.bottomNav.selectedItemId = R.id.nav_downloader
                    viewModel.fetchMedia(extractedUrl, preferences.engineMode)
                    UiUtils.showToast(this, "Tautan TikTok diterima: ${extractedUrl.take(50)}...")
                }
            }
        }
    }
}
