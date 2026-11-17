package com.iset.fastcolloc.ui.listings
import android.os.Bundle
import android.view.View
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentPublicationSuccessBinding
class PublicationSuccessFragment : ListingBoundFragment<FragmentPublicationSuccessBinding>(FragmentPublicationSuccessBinding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.tvPublishedTitle.text = requireArguments().getString("title").orEmpty()
        binding.btnViewPublished.setOnClickListener {
            findNavController().navigate(R.id.listingDetailFragment, Bundle().apply { putString("listingId", requireArguments().getString("listingId")) }, NavOptions.Builder().setPopUpTo(R.id.publicationSuccessFragment, true).setLaunchSingleTop(true).build())
        }
        binding.btnMyListings.setOnClickListener { findNavController().navigate(R.id.mineListingsFragment, null, NavOptions.Builder().setPopUpTo(R.id.homeFragment, false).build()) }
    }
}
