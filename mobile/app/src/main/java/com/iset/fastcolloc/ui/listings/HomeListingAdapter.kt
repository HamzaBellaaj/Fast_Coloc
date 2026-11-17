package com.iset.fastcolloc.ui.listings

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.iset.fastcolloc.R
import com.iset.fastcolloc.data.remote.ListingDto
import com.iset.fastcolloc.databinding.ItemHomeListingBinding
import java.text.NumberFormat
import java.util.Locale

/** Compact photo cards for the home carousel; catalog cards keep their own layout. */
class HomeListingAdapter(private val open: (ListingDto) -> Unit, private val favorite: (ListingDto) -> Unit) :
    ListAdapter<ListingDto, HomeListingAdapter.Holder>(object : DiffUtil.ItemCallback<ListingDto>() {
        override fun areItemsTheSame(a: ListingDto, b: ListingDto) = a.id == b.id
        override fun areContentsTheSame(a: ListingDto, b: ListingDto) = a == b
    }) {
    inner class Holder(private val b: ItemHomeListingBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: ListingDto) {
            b.tvTitle.text = row.title
            b.tvPlace.text = "${row.locality} · ${row.capacity} pers."
            b.tvKind.text = if (row.kind == "OFFER") "Logement disponible" else "Recherche de logement"
            val amount = NumberFormat.getNumberInstance(Locale.FRANCE).apply { maximumFractionDigits = 3 }.format(row.rent)
            b.tvRent.text = if (row.kind == "OFFER") "$amount TND / mois" else "Budget · $amount TND / mois"
            b.btnFavorite.contentDescription = if (row.favorite) "Retirer des favoris : ${row.title}" else "Ajouter aux favoris : ${row.title}"
            b.btnFavorite.iconTint = ColorStateList.valueOf(ContextCompat.getColor(b.root.context, if (row.favorite) R.color.fc_white else R.color.fc_violet))
            b.btnFavorite.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(b.root.context, if (row.favorite) R.color.fc_violet else R.color.fc_white))
            b.ivPhoto.contentDescription = row.photos.firstOrNull()?.caption ?: row.title
            b.ivPhoto.scaleType = if (row.photos.firstOrNull()?.url.isNullOrBlank()) android.widget.ImageView.ScaleType.CENTER_INSIDE else android.widget.ImageView.ScaleType.CENTER_CROP
            b.ivPhoto.load(row.photos.firstOrNull()?.url) {
                placeholder(R.drawable.ic_fastcoloc_symbol); error(R.drawable.ic_fastcoloc_symbol)
                diskCachePolicy(CachePolicy.DISABLED)
            }
            b.root.setOnClickListener { open(row) }
            b.btnFavorite.setOnClickListener { favorite(row) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemHomeListingBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}
