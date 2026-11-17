package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentHomeBinding

class HomeFragment : BoundFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        vm.details.observe(viewLifecycleOwner) { details ->
            details?.profile?.let { profile ->
                binding.tvWelcome.text = getString(R.string.s1_welcome, profile.firstName.orEmpty())
                binding.btnContinue.isVisible = profile.completedAt == null
            }
        }
        binding.btnProfile.setOnClickListener { findNavController().navigate(R.id.profileFragment) }
        binding.btnListings.setOnClickListener { findNavController().navigate(R.id.listingCatalogFragment) }
        binding.btnContinue.setOnClickListener { vm.continueRegistration() }
        binding.btnLogout.setOnClickListener { vm.logout() }
        vm.busy.observe(viewLifecycleOwner) { binding.btnProfile.isEnabled = !it; binding.btnContinue.isEnabled = !it; binding.btnLogout.isEnabled = !it }
        vm.refresh()
    }
}
