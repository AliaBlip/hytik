package com.aliablip.hytik.ui.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.aliablip.hytik.data.preferences.AppPreferences
import com.aliablip.hytik.databinding.FragmentDownloaderBinding
import com.aliablip.hytik.ui.adapters.PhotoSlideAdapter
import com.aliablip.hytik.ui.dialogs.DownloadProgressDialog
import com.aliablip.hytik.ui.main.MainViewModel
import com.aliablip.hytik.ui.main.MainViewModelFactory
import com.aliablip.hytik.ui.utils.UiUtils
import kotlinx.coroutines.launch

class DownloaderFragment : Fragment() {

    private var _binding: FragmentDownloaderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels { MainViewModelFactory() }
    private lateinit var preferences: AppPreferences

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
                        } else {
                            binding.shimmerLoading.stopShimmer()
                            binding.shimmerLoading.visibility = View.GONE
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

        val statsText = buildString {
            if (media.likeCount > 0) append("${media.likeCount} Suka • ")
            if (media.commentCount > 0) append("${media.commentCount} Komentar • ")
            if (media.durationSec > 0) append("${media.durationSec}s")
            else append("Media TikTok")
        }
        binding.tvMediaStats.text = statsText

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

            binding.btnDownloadHd.setOnClickListener {
                val url = media.getBestVideoUrl()
                if (url != null) {
                    downloadFile(
                        url = url,
                        fileName = "HyTik_HD_${media.id}.mp4",
                        mediaType = "VIDEO",
                        title = media.title,
                        author = media.authorName,
                        cover = media.coverUrl
                    )
                } else {
                    UiUtils.showToast(requireContext(), "Video HD tidak tersedia untuk postingan ini")
                }
            }

            binding.btnDownloadNoWm.setOnClickListener {
                val url = media.videoNoWmUrl ?: media.videoHdNoWmUrl ?: media.videoWmUrl
                if (url != null) {
                    downloadFile(
                        url = url,
                        fileName = "HyTik_${media.id}.mp4",
                        mediaType = "VIDEO",
                        title = media.title,
                        author = media.authorName,
                        cover = media.coverUrl
                    )
                } else {
                    UiUtils.showToast(requireContext(), "URL Video tidak ditemukan")
                }
            }

            binding.btnDownloadWm.setOnClickListener {
                val url = media.videoWmUrl ?: media.videoNoWmUrl
                if (url != null) {
                    downloadFile(
                        url = url,
                        fileName = "HyTik_WM_${media.id}.mp4",
                        mediaType = "VIDEO",
                        title = "${media.title} (With Watermark)",
                        author = media.authorName,
                        cover = media.coverUrl
                    )
                } else {
                    UiUtils.showToast(requireContext(), "URL Watermark tidak ditemukan")
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
        var isCancelled = false
        val dialog = DownloadProgressDialog(requireContext(), title) {
            isCancelled = true
        }
        dialog.show()

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
                    }
                }
            )

            dialog.dismiss()

            if (!isCancelled) {
                result.onSuccess {
                    UiUtils.showSnackbar(binding.root, "Berhasil mengunduh: $title")
                }.onFailure { ex ->
                    UiUtils.showSnackbar(binding.root, "Gagal mengunduh: ${ex.message}")
                }
            }
        }
    }

    private fun downloadAllPhotos(media: TikTokMediaResult) {
        var isCancelled = false
        val dialog = DownloadProgressDialog(requireContext(), "Mengunduh ${media.photoUrls.size} Foto...") {
            isCancelled = true
        }
        dialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val total = media.photoUrls.size
            var successCount = 0

            for ((index, photoUrl) in media.photoUrls.withIndex()) {
                if (isCancelled) break

                val idx = index + 1
                requireActivity().runOnUiThread {
                    dialog.updateProgress(((index * 100) / total), 0, 0)
                }

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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
