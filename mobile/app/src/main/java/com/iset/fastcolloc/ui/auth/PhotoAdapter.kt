package com.iset.fastcolloc.ui.auth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.iset.fastcolloc.data.remote.PhotoDto
import com.iset.fastcolloc.databinding.ItemPhotoBinding

data class PhotoItem(val photo: PhotoDto, val url: String?)
class PhotoAdapter(private val save: (String, String) -> Unit, private val remove: (String) -> Unit) : ListAdapter<PhotoItem, PhotoAdapter.Holder>(object : DiffUtil.ItemCallback<PhotoItem>() {
    override fun areItemsTheSame(old: PhotoItem, new: PhotoItem) = old.photo.id == new.photo.id
    override fun areContentsTheSame(old: PhotoItem, new: PhotoItem) = old == new
}) {
    var busy = false
    class Holder(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            ivPhoto.load(item.url) {
                crossfade(true)
                diskCachePolicy(coil.request.CachePolicy.DISABLED)
                memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            }
            etCaption.setText(item.photo.caption)
            btnSave.setOnClickListener { if(!busy) save(item.photo.id, etCaption.text.toString()) }
            btnRemovePhoto.setOnClickListener { if(!busy) remove(item.photo.id) }
        }
    }
}
