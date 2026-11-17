package com.iset.fastcolloc.ui.listings

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.iset.fastcolloc.R
import com.iset.fastcolloc.ui.auth.BoundFragment
import com.iset.fastcolloc.viewmodel.*

abstract class ListingBoundFragment<B : ViewBinding>(
    inflate: (LayoutInflater, ViewGroup?, Boolean) -> B
) : BoundFragment<B>(inflate) {
    protected val listings: ListingViewModel by activityViewModels {
        ListingViewModelFactory(requireActivity().application)
    }

    protected fun observeMessages() {
        listings.message.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                listings.consumeMessage()
            }
        }
        listings.event.observe(viewLifecycleOwner) { event ->
            event.consume()?.let { route ->
                when {
                    route == "login" ->
                        findNavController()
                            .navigate(
                                R.id.loginFragment,
                                null,
                                NavOptions.Builder().setPopUpTo(R.id.nav_auth, false).build(),
                            )
                    route == "list" ->
                        findNavController()
                            .navigate(
                                R.id.listingCatalogFragment,
                                null,
                                NavOptions.Builder().setPopUpTo(R.id.homeFragment, false).build(),
                            )
                    route.startsWith("detail:") ->
                        findNavController()
                            .navigate(
                                R.id.listingDetailFragment,
                                Bundle().apply {
                                    putString("listingId", route.removePrefix("detail:"))
                                },
                                NavOptions.Builder()
                                    .setPopUpTo(R.id.listingEditorFragment, true)
                                    .build(),
                            )
                }
            }
        }
    }
}
