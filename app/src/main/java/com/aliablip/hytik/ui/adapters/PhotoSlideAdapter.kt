package com.aliablip.hytik.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.aliablip.hytik.databinding.ItemPhotoSlideBinding

class PhotoSlideAdapter(
    private val photoUrls: List<String>,
    private val onDownloadPhoto: (url: String, index: Int) -> Unit
) : RecyclerView.Adapter<PhotoSlideAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(val binding: ItemPhotoSlideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val url = photoUrls[position]
        holder.binding.tvPhotoIndex.text = "Foto #${position + 1} dari ${photoUrls.size}"
        holder.binding.ivPhotoSlide.load(url) {
            crossfade(true)
        }
        holder.binding.btnDownloadSinglePhoto.setOnClickListener {
            onDownloadPhoto(url, position + 1)
        }
    }

    override fun getItemCount(): Int = photoUrls.size
}
