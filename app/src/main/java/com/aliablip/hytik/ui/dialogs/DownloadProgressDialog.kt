package com.aliablip.hytik.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import com.aliablip.hytik.databinding.DialogDownloadProgressBinding
import com.aliablip.hytik.ui.utils.FileUtils

class DownloadProgressDialog(
    context: Context,
    private val title: String,
    private val onCancel: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogDownloadProgressBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogDownloadProgressBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        setCancelable(false)
        setCanceledOnTouchOutside(false)

        binding.tvProgressSubtitle.text = title
        binding.btnCancelDownload.setOnClickListener {
            onCancel()
            dismiss()
        }
    }

    fun updateProgress(percent: Int, downloadedKb: Long, totalKb: Long) {
        if (!::binding.isInitialized) return
        binding.progressBar.progress = percent
        binding.tvPercentage.text = "$percent%"
        if (totalKb > 0) {
            binding.tvSpeedSize.text = "${FileUtils.formatSizeKb(downloadedKb)} / ${FileUtils.formatSizeKb(totalKb)}"
        } else {
            binding.tvSpeedSize.text = "${FileUtils.formatSizeKb(downloadedKb)} terunduh"
        }
    }
}
