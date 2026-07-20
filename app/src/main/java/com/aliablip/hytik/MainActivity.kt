package com.aliablip.hytik

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.aliablip.hytik.data.preferences.AppPreferences
import com.aliablip.hytik.databinding.ActivityMainBinding
import com.aliablip.hytik.ui.main.MainViewModel
import com.aliablip.hytik.ui.main.MainViewModelFactory
import com.aliablip.hytik.ui.tabs.DownloaderFragment
import com.aliablip.hytik.ui.tabs.HistoryFragment
import com.aliablip.hytik.ui.tabs.SettingsFragment
import com.aliablip.hytik.ui.utils.NetworkUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory() }
    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)

        setupNavigation()
        handleIncomingIntent(intent)
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

        // Disable user swipe on ViewPager to prevent accidental horizontal gesture conflicts with photo slides
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
                }
            }
        }
    }
}
