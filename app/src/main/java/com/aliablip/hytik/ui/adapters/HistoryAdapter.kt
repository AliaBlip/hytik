package com.aliablip.hytik.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.aliablip.hytik.R
import com.aliablip.hytik.data.db.DownloadHistoryEntity
import com.aliablip.hytik.databinding.ItemHistoryBinding
import com.aliablip.hytik.ui.utils.FileUtils

class HistoryAdapter(
    private val onOpen: (DownloadHistoryEntity) -> Unit,
    private val onShare: (DownloadHistoryEntity) -> Unit,
    private val onDelete: (DownloadHistoryEntity) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var items = listOf<DownloadHistoryEntity>()

    fun submitList(newItems: List<DownloadHistoryEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvHistoryTitle.text = item.title
        holder.binding.tvHistoryAuthor.text = item.authorName
        holder.binding.tvHistoryDate.text = FileUtils.formatDate(item.downloadedAtMillis)
        
        val badgeText = when (item.mediaType) {
            "VIDEO" -> "VIDEO (${FileUtils.formatSizeKb(item.fileSizeKb)})"
            "AUDIO" -> "AUDIO MP3 (${FileUtils.formatSizeKb(item.fileSizeKb)})"
            else -> "FOTO (${FileUtils.formatSizeKb(item.fileSizeKb)})"
        }
        holder.binding.tvHistoryTypeBadge.text = badgeText

        val iconRes = when (item.mediaType) {
            "VIDEO" -> R.drawable.ic_video
            "AUDIO" -> R.drawable.ic_music
            else -> R.drawable.ic_photo
        }
        holder.binding.ivTypeIconOverlay.setImageResource(iconRes)

        holder.binding.ivThumb.load(item.coverUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_card_rounded)
            error(R.drawable.bg_card_rounded)
        }

        holder.binding.btnOpen.setOnClickListener { onOpen(item) }
        holder.binding.btnShare.setOnClickListener { onShare(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}
