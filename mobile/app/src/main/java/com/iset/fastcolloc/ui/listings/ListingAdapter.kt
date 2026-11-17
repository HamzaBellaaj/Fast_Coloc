package com.iset.fastcolloc.ui.listings

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.iset.fastcolloc.R
import com.iset.fastcolloc.data.remote.ListingDto
import com.iset.fastcolloc.databinding.ItemListingBinding

/** Chap.4: RecyclerView, ViewHolder, ViewBinding and lambda callbacks. */
class ListingAdapter(
    private val open: (ListingDto) -> Unit,
    private val favorite: (ListingDto) -> Unit,
) :
    ListAdapter<ListingDto, ListingAdapter.Holder>(
        object : DiffUtil.ItemCallback<ListingDto>() {
            override fun areItemsTheSame(a: ListingDto, b: ListingDto) = a.id == b.id

            override fun areContentsTheSame(a: ListingDto, b: ListingDto) = a == b
        }
    ) {
    var enabled = true

    inner class Holder(private val b: ItemListingBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: ListingDto) {
            b.tvTitle.text = row.title
            b.tvPlace.text = "${row.governorate} · ${row.locality}"
            b.tvRent.text = b.root.context.getString(R.string.s2_price, row.rent)
            b.tvInfo.text =
                "${if(row.kind=="OFFER")"Offre" else "Demande"} · ${row.capacity} personne(s) · ${row.propertyType}"
            b.tvStatus.visibility = if (row.status == "DRAFT") View.VISIBLE else View.GONE
            b.tvStatus.text =
                if (row.status == "DRAFT") "Brouillon"
                else "Publiée jusqu’au ${row.expiresAt?.take(10)}"
            b.btnFavorite.text =
                if (row.favorite) "Retirer des favoris" else "Enregistrer dans mes favoris"
            b.btnFavorite.isEnabled = enabled && row.status == "PUBLISHED"
            b.ivPhoto.contentDescription = row.photos.firstOrNull()?.caption ?: row.title
            b.ivPhoto.scaleType = if (row.photos.firstOrNull()?.url.isNullOrBlank()) android.widget.ImageView.ScaleType.CENTER_INSIDE else android.widget.ImageView.ScaleType.CENTER_CROP
            b.ivPhoto.load(row.photos.firstOrNull()?.url) {
                placeholder(R.drawable.ic_fastcoloc_symbol)
                error(R.drawable.ic_fastcoloc_symbol)
                diskCachePolicy(CachePolicy.DISABLED)
                memoryCachePolicy(CachePolicy.ENABLED)
            }
            b.root.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); open(row) }
            b.btnFavorite.setOnClickListener { com.iset.fastcolloc.ui.motion.Motion.press(it); favorite(row) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        Holder(ItemListingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    override fun onViewRecycled(holder: Holder) { holder.itemView.animate().cancel(); holder.itemView.scaleX=1f; holder.itemView.scaleY=1f; super.onViewRecycled(holder) }
}
