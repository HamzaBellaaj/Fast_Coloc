package com.iset.fastcolloc.ui.listings

import android.view.*
import androidx.core.view.isVisible
import androidx.recyclerview.widget.*
import coil.load
import coil.request.CachePolicy
import com.iset.fastcolloc.R
import com.iset.fastcolloc.data.remote.ListingPhotoDto
import com.iset.fastcolloc.databinding.ItemListingPhotoBinding

class ListingPhotoAdapter(private val remove: ((String) -> Unit)? = null) :
    ListAdapter<ListingPhotoDto, ListingPhotoAdapter.Holder>(
        object : DiffUtil.ItemCallback<ListingPhotoDto>() {
            override fun areItemsTheSame(a: ListingPhotoDto, b: ListingPhotoDto) = a.id == b.id

            override fun areContentsTheSame(a: ListingPhotoDto, b: ListingPhotoDto) = a == b
        }
    ) {
    var enabled = true

    inner class Holder(private val b: ItemListingPhotoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: ListingPhotoDto) {
            b.ivPhoto.load(row.url) {
                placeholder(R.drawable.ic_fastcoloc_symbol)
                error(R.drawable.ic_fastcoloc_symbol)
                diskCachePolicy(CachePolicy.DISABLED)
                memoryCachePolicy(CachePolicy.DISABLED)
            }
            b.ivPhoto.contentDescription = row.caption.ifBlank { "Photo de l’annonce" }
            b.tvCaption.text = row.caption
            b.btnDeletePhoto.isVisible = remove != null
            b.btnDeletePhoto.isEnabled = enabled
            b.btnDeletePhoto.setOnClickListener { remove?.invoke(row.id) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        Holder(ItemListingPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}
