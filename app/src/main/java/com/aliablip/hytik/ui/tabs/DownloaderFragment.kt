package com.aliablip.hytik.ui.tabs

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.aliablip.hytik.R
import com.aliablip.hytik.data.api.models.MediaType
import com.aliablip.hytik.data.api.models.TikTokMediaResult
import com.aliablip.hytik.data.downloader.FastMediaDownloader
import com.aliablip.hytik.data.notification.DownloadNotificationHelper
import com.aliablip.hytik.data.preferences.AppPreferences
import com.aliablip.hytik.databinding.FragmentDownloaderBinding
import com.aliablip.hytik.ui.adapters.PhotoSlideAdapter
import com.aliablip.hytik.ui.dialogs.DownloadProgressDialog
import com.aliablip.hytik.ui.main.MainViewModel
import com.aliablip.hytik.ui.main.MainViewModelFactory
import com.aliablip.hytik.ui.utils.PermissionManager
import com.aliablip.hytik.ui.utils.UiUtils
import kotlinx.coroutines.launch
import kotlin.random.Random

class DownloaderFragment : Fragment() {

    private var _binding: FragmentDownloaderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels { MainViewModelFactory() }
    private lateinit var preferences: AppPreferences

    // untuk permission
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.entries.all { it.value }
        if (allGranted) {
            UiUtils.showSnackbar(binding.root, "Izin media & notifikasi diaktifkan. HyTik siap menyimpan file.")
        } else {
            showPermissionRationale()
        }
    }

    // untuk cek akses media saat pertama kali
    private var hasCheckedPermissionOnResume = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = AppPreferences(requireContext())

        setupListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkClipboard(requireContext(), preferences.isAutoPasteEnabled)
        if (!hasCheckedPermissionOnResume) {
            hasCheckedPermissionOnResume = true
            checkMediaPermissionBanner()
        }
    }

    private fun checkMediaPermissionBanner() {
        if (!PermissionManager.hasMediaPermissions(requireContext())) {
            binding.cardPermissionBanner.visibility = View.VISIBLE
            binding.tvPermissionBanner.text = "HyTik butuh akses media untuk menyimpan video & foto ke galeri. Tap untuk aktifkan."
            binding.btnGrantPermission.setOnClickListener {
                requestMediaPermissions()
            }
        } else if (!PermissionManager.hasNotificationPermission(requireContext()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.cardPermissionBanner.visibility = View.VISIBLE
            binding.tvPermissionBanner.text = "Aktifkan notifikasi agar progress download muncul di bar notifikasi."
            binding.btnGrantPermission.setOnClickListener {
                requestMediaPermissions()
            }
        } else {
            binding.cardPermissionBanner.visibility = View.GONE
        }
    }

    private fun requestMediaPermissions() {
        val missing = PermissionManager.getMissingPermissions(requireContext())
        if (missing.isEmpty()) {
            binding.cardPermissionBanner.visibility = View.GONE
            return
        }
        permissionLauncher.launch(missing)
    }

    private fun showPermissionRationale() {
        val missing = PermissionManager.getMissingPermissions(requireContext())
        if (missing.isEmpty()) return

        val msg = missing.joinToString("\n\n") { perm ->
            "• ${PermissionManager.getPermissionRationale(perm)}"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Izin Diperlukan")
            .setMessage("Untuk pengalaman profesional, HyTik memerlukan izin berikut:\n\n$msg\n\nTanpa izin ini, file tetap bisa disimpan (Android 10+), tapi akses galeri & notifikasi tidak optimal.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                PermissionManager.openAppSettings(requireContext())
            }
            .setNegativeButton("Nanti") { d, _ -> d.dismiss() }
            .show()
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            val url = binding.etUrlInput.text?.toString()?.trim() ?: ""
            if (url.isNotEmpty()) {
                UiUtils.hideKeyboard(requireActivity())
                viewModel.fetchMedia(url, preferences.engineMode)
            }
            binding.swipeRefresh.isRefreshing = false
        }

        binding.btnPaste.setOnClickListener {
            val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val text = cm?.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrBlank()) {
                binding.etUrlInput.setText(text)
                binding.etUrlInput.setSelection(text.length)
            } else {
                UiUtils.showToast(requireContext(), "Papan klip kosong")
            }
        }

        binding.btnClear.setOnClickListener {
            binding.etUrlInput.setText("")
            viewModel.clearResult()
        }

        binding.btnFetch.setOnClickListener {
            val url = binding.etUrlInput.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                UiUtils.showSnackbar(binding.root, getString(R.string.error_invalid_url))
                return@setOnClickListener
            }
            UiUtils.hideKeyboard(requireActivity())
            viewModel.fetchMedia(url, preferences.engineMode)
        }

        binding.btnPasteClipboard.setOnClickListener {
            val clipboardUrl = viewModel.clipboardUrlDetected.value
            if (!clipboardUrl.isNullOrBlank()) {
                binding.etUrlInput.setText(clipboardUrl)
                binding.etUrlInput.setSelection(clipboardUrl.length)
                viewModel.dismissClipboardNotification()
                UiUtils.hideKeyboard(requireActivity())
                viewModel.fetchMedia(clipboardUrl, preferences.engineMode)
            }
        }

        binding.btnGrantPermission.setOnClickListener {
            requestMediaPermissions()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoading.collect { loading ->
                        if (loading) {
                            binding.shimmerLoading.visibility = View.VISIBLE
                            binding.shimmerLoading.startShimmer()
                            binding.cardResult.visibility = View.GONE
                            binding.btnFetch.isEnabled = false
                        } else {
                            binding.shimmerLoading.stopShimmer()
                            binding.shimmerLoading.visibility = View.GONE
                            binding.btnFetch.isEnabled = true
                        }
                    }
                }

                launch {
                    viewModel.errorMessage.collect { error ->
                        if (error != null) {
                            UiUtils.showSnackbar(binding.root, error)
                        }
                    }
                }

                launch {
                    viewModel.mediaResult.collect { result ->
                        if (result != null) {
                            displayResult(result)
                        } else {
                            binding.cardResult.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.clipboardUrlDetected.collect { clipboardUrl ->
                        if (clipboardUrl != null && binding.etUrlInput.text?.toString() != clipboardUrl) {
                            binding.cardClipboardNotification.visibility = View.VISIBLE
                        } else {
                            binding.cardClipboardNotification.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun displayResult(media: TikTokMediaResult) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvMediaTitle.text = media.title
        binding.tvAuthorName.text = media.authorName
        binding.tvAuthorHandle.text = media.authorHandle

        binding.tvMediaStats.text = media.getDisplayStats()

        binding.ivAuthorAvatar.load(media.authorAvatarUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_card_rounded)
            error(R.drawable.bg_card_rounded)
        }

        binding.ivMediaCover.load(media.coverUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_card_rounded)
        }

        if (media.mediaType == MediaType.PHOTO_CAROUSEL && media.photoUrls.isNotEmpty()) {
            binding.sectionVideoButtons.visibility = View.GONE
            binding.sectionPhotoCarousel.visibility = View.VISIBLE
            binding.cardCoverContainer.visibility = View.GONE

            binding.tvPhotoSlideCount.text = getString(R.string.photo_slide_header, media.photoUrls.size)
            binding.btnDownloadAllPhotos.text = getString(R.string.download_all_photos, media.photoUrls.size)

            val adapter = PhotoSlideAdapter(media.photoUrls) { photoUrl, idx ->
                downloadFile(
                    url = photoUrl,
                    fileName = "HyTik_Photo_${media.id}_$idx.jpg",
                    mediaType = "PHOTO",
                    title = "${media.title} (Foto #$idx)",
                    author = media.authorName,
                    cover = photoUrl
                )
            }
            binding.rvPhotoSlides.layoutManager = LinearLayoutManager(requireContext())
            binding.rvPhotoSlides.adapter = adapter

            binding.btnDownloadAllPhotos.setOnClickListener {
                downloadAllPhotos(media)
            }
        } else {
            binding.sectionVideoButtons.visibility = View.VISIBLE
            binding.sectionPhotoCarousel.visibility = View.GONE
            binding.cardCoverContainer.visibility = View.VISIBLE

            // HD button - selalu ada fallback ke best URL agar tidak "tidak dapat menemukan link"
            binding.btnDownloadHd.setOnClickListener {
                val url = media.getHdUrl() ?: media.getBestVideoUrl()
                if (!url.isNullOrBlank()) {
                    downloadFile(
                        url = url,
                        fileName = "HyTik_HD_${media.id}.mp4",
                        mediaType = "VIDEO",
                        title = media.title,
                        author = media.authorName,
                        cover = media.coverUrl
                    )
                } else {
                    UiUtils.showSnackbar(binding.root, "Link HD belum tersedia. Coba gunakan mode ${preferences.engineMode.getDisplayName()}.")
                }
            }

            binding.btnDownloadNoWm.setOnClickListener {
                val url = media.getSdUrl() ?: media.getBestVideoUrl()
                if (!url.isNullOrBlank()) {
                    downloadFile(
                        url = url,
                        fileName = "HyTik_${media.id}.mp4",
                        mediaType = "VIDEO",
                        title = media.title,
                        author = media.authorName,
                        cover = media.coverUrl
                    )
                } else {
                    UiUtils.showSnackbar(binding.root, "Link video tidak ditemukan. Pastikan video tidak diprivat.")
                }
            }

            binding.btnDownloadWm.setOnClickListener {
                val url = media.getWmUrl() ?: media.getBestVideoUrl()
                if (!url.isNullOrBlank()) {
                    downloadFile(
                        url = url,
                        fileName = "HyTik_WM_${media.id}.mp4",
                        mediaType = "VIDEO",
                        title = "${media.title} (Watermark)",
                        author = media.authorName,
                        cover = media.coverUrl
                    )
                } else {
                    // still use best url as fallback for watermark request
                    val fallback = media.getBestVideoUrl()
                    if (!fallback.isNullOrBlank()) {
                        downloadFile(
                            url = fallback,
                            fileName = "HyTik_WM_${media.id}.mp4",
                            mediaType = "VIDEO",
                            title = "${media.title} (Watermark)",
                            author = media.authorName,
                            cover = media.coverUrl
                        )
                    } else {
                        UiUtils.showSnackbar(binding.root, "Link watermark tidak tersedia.")
                    }
                }
            }
        }

        if (!media.audioMp3Url.isNullOrBlank()) {
            binding.btnDownloadAudio.visibility = View.VISIBLE
            binding.btnDownloadAudio.setOnClickListener {
                downloadFile(
                    url = media.audioMp3Url,
                    fileName = "HyTik_Audio_${media.id}.mp3",
                    mediaType = "AUDIO",
                    title = "${media.title} (Audio MP3)",
                    author = media.authorName,
                    cover = media.coverUrl
                )
            }
        } else {
            binding.btnDownloadAudio.visibility = View.GONE
        }
    }

    private fun downloadFile(url: String, fileName: String, mediaType: String, title: String, author: String, cover: String) {
        // Cek izin ringan - walau Android Q+ tidak wajib, tetap ingatkan
        if (!PermissionManager.hasMediaPermissions(requireContext())) {
            UiUtils.showSnackbar(binding.root, "HyTik memerlukan akses media untuk penyimpanan optimal. Memberikan izin...")
            // tetap lanjutkan download karena MediaStore Q+ tetap bisa tanpa izin, tapi tawarkan request
        }

        var isCancelled = false
        val dialog = DownloadProgressDialog(requireContext(), title) {
            isCancelled = true
        }
        dialog.show()

        val notifId = Random.nextInt(1000, 9999)
        // initial notification
        DownloadNotificationHelper.showProgressNotification(
            requireContext(),
            notifId,
            title,
            fileName,
            0
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result = FastMediaDownloader.downloadMedia(
                context = requireContext(),
                url = url,
                fileName = fileName,
                mediaType = mediaType,
                title = title,
                authorName = author,
                coverUrl = cover,
                onProgress = { percent, downloadedKb, totalKb ->
                    if (!isCancelled) {
                        requireActivity().runOnUiThread {
                            dialog.updateProgress(percent, downloadedKb, totalKb)
                        }
                        // update notif bar juga
                        DownloadNotificationHelper.showProgressNotification(
                            requireContext(),
                            notifId,
                            title,
                            fileName,
                            percent
                        )
                    }
                }
            )

            dialog.dismiss()

            if (!isCancelled) {
                result.onSuccess { uri ->
                    UiUtils.showSnackbar(binding.root, "Berhasil mengunduh: $title")
                    DownloadNotificationHelper.showCompletedNotification(
                        requireContext(),
                        notifId,
                        title,
                        uri,
                        mediaType
                    )
                }.onFailure { ex ->
                    val msg = ex.message ?: "Gagal mengunduh"
                    UiUtils.showSnackbar(binding.root, msg)
                    DownloadNotificationHelper.showFailedNotification(
                        requireContext(),
                        notifId,
                        title,
                        msg
                    )
                }
            } else {
                DownloadNotificationHelper.cancelNotification(requireContext(), notifId)
            }
        }
    }

    private fun downloadAllPhotos(media: TikTokMediaResult) {
        var isCancelled = false
        val dialog = DownloadProgressDialog(requireContext(), "Mengunduh ${media.photoUrls.size} Foto...") {
            isCancelled = true
        }
        dialog.show()

        val batchNotifId = Random.nextInt(1000, 9999)

        viewLifecycleOwner.lifecycleScope.launch {
            val total = media.photoUrls.size
            var successCount = 0

            for ((index, photoUrl) in media.photoUrls.withIndex()) {
                if (isCancelled) break

                val idx = index + 1
                requireActivity().runOnUiThread {
                    dialog.updateProgress(((index * 100) / total), 0, 0)
                }

                DownloadNotificationHelper.showProgressNotification(
                    requireContext(),
                    batchNotifId,
                    "Mengunduh ${total} Foto",
                    "Foto $idx dari $total",
                    ((index * 100) / total)
                )

                val result = FastMediaDownloader.downloadMedia(
                    context = requireContext(),
                    url = photoUrl,
                    fileName = "HyTik_Photo_${media.id}_$idx.jpg",
                    mediaType = "PHOTO",
                    title = "${media.title} (Foto #$idx)",
                    authorName = media.authorName,
                    coverUrl = photoUrl,
                    onProgress = { _, _, _ -> }
                )
                if (result.isSuccess) successCount++
            }

            dialog.dismiss()
            if (!isCancelled) {
                UiUtils.showSnackbar(binding.root, "Berhasil mengunduh $successCount dari $total foto!")
                DownloadNotificationHelper.showCompletedNotification(
                    requireContext(),
                    batchNotifId,
                    "Download $total Foto Selesai",
                    null,
                    "PHOTO"
                )
            } else {
                DownloadNotificationHelper.cancelNotification(requireContext(), batchNotifId)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
