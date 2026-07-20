package com.aliablip.hytik.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliablip.hytik.data.api.models.TikTokMediaResult
import com.aliablip.hytik.data.engine.EngineMode
import com.aliablip.hytik.data.engine.HyTikExtractorEngine
import com.aliablip.hytik.ui.utils.ClipboardUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val engine = HyTikExtractorEngine()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mediaResult = MutableStateFlow<TikTokMediaResult?>(null)
    val mediaResult: StateFlow<TikTokMediaResult?> = _mediaResult.asStateFlow()

    private val _clipboardUrlDetected = MutableStateFlow<String?>(null)
    val clipboardUrlDetected: StateFlow<String?> = _clipboardUrlDetected.asStateFlow()

    fun checkClipboard(context: Context, isAutoPasteEnabled: Boolean) {
        if (!isAutoPasteEnabled) return
        val url = ClipboardUtils.getTikTokUrlFromClipboard(context)
        if (url != null && (_mediaResult.value == null || _mediaResult.value?.id == "")) {
            _clipboardUrlDetected.value = url
        } else {
            _clipboardUrlDetected.value = null
        }
    }

    fun dismissClipboardNotification() {
        _clipboardUrlDetected.value = null
    }

    fun fetchMedia(url: String, mode: EngineMode) {
        if (url.isBlank()) {
            _errorMessage.value = "Tautan tidak boleh kosong!"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _mediaResult.value = null

            val result = engine.extractMedia(url, mode)
            result.onSuccess { media ->
                _mediaResult.value = media
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Gagal mengekstrak data dari tautan TikTok."
            }

            _isLoading.value = false
        }
    }

    fun clearResult() {
        _mediaResult.value = null
        _errorMessage.value = null
    }
}
