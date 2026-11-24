package com.iset.fastcolloc.ui.listings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentListingDetailBinding

class ListingDetailFragment :
    ListingBoundFragment<FragmentListingDetailBinding>(FragmentListingDetailBinding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        observeMessages()
        val id = requireArguments().getString("listingId") ?: return
        val photos = ListingPhotoAdapter()
        binding.rvPhotos.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPhotos.adapter = photos
        fun render() {
            val row = listings.selected.value?.takeIf { it.id == id }
            val busy = listings.busy.value == true
            val offline = listings.offline.value == true
            binding.housingSkeleton.isVisible = busy && row == null
            binding.tvState.text =
                if (busy) "Chargement…"
                else if (offline) "Copie locale · informations à vérifier en ligne."
                else if (row == null) "Annonce indisponible." else "Informations du serveur"
            binding.btnRefreshListing.isEnabled = !busy
            val own = row != null && row.authorId == listings.owner.value
            binding.btnContact.isVisible = row != null && !own && row.status == "PUBLISHED" && !offline
            binding.btnContact.isEnabled = !busy && !offline
            binding.btnEditListing.isVisible = own
            binding.btnRenewListing.isVisible = own
            binding.btnDeleteListing.isVisible = own
            listOf(
                    binding.btnEditListing,
                    binding.btnRenewListing,
                    binding.btnDeleteListing,
                    binding.btnFavorite,
                )
                .forEach { it.isEnabled = !busy && !offline && row != null }
            binding.btnFavorite.isEnabled = !busy && !offline && row?.status == "PUBLISHED"
            if (row == null) return
            binding.tvTitle.text = row.title
            binding.tvRent.text = getString(R.string.s2_price, row.rent)
            binding.tvDescription.text = row.description
            binding.tvPlace.text = "${row.street}, ${row.locality} · ${row.governorate}"
            binding.tvInfo.text =
                "${row.propertyType} · ${row.capacity} personne(s)\n${row.roommateType.ifBlank{"Aucune préférence de colocataire"}}"
            binding.tvEquipment.text = row.equipment.joinToString(" · ")
            binding.tvAvailable.text =
                "Disponible dès le ${row.availableFrom}" +
                    (row.availableUntil?.let { " jusqu’au $it" } ?: "")
            binding.tvExpiry.text =
                if (row.status == "DRAFT") "Brouillon"
                else "Publiée jusqu’au ${row.expiresAt?.take(10)}"
            binding.btnFavorite.text =
                if (row.favorite) "♥ Retirer des favoris" else "♡ Ajouter aux favoris"
            photos.submitList(row.photos)
        }
        listings.selected.observe(viewLifecycleOwner) { render() }
        listings.busy.observe(viewLifecycleOwner) { render() }
        listings.offline.observe(viewLifecycleOwner) { render() }
        listings.owner.observe(viewLifecycleOwner) { render() }
        binding.btnContact.setOnClickListener { listings.selected.value?.let { row -> findNavController().navigate(R.id.conversationFragment, Bundle().apply { putString("listingId",row.id);putString("listingTitle",row.title) }) } }
        binding.btnRefreshListing.setOnClickListener { listings.detail(id) }
        binding.btnFavorite.setOnClickListener { listings.selected.value?.let(listings::favorite) }
        binding.btnEditListing.setOnClickListener {
            listings.beginEditor(id)
            findNavController()
                .navigate(R.id.listingEditorFragment, Bundle().apply { putString("listingId", id) })
        }
        binding.btnRenewListing.setOnClickListener { listings.publish() }
        binding.btnDeleteListing.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.s2_confirm_delete)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer") { _, _ -> listings.delete() }
                .show()
        }
        listings.detail(id)
    }

    override fun onDestroyView() {
        binding.rvPhotos.adapter = null
        super.onDestroyView()
    }
}
