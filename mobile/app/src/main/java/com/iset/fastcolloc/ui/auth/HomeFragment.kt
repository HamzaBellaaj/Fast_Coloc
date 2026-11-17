package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentHomeBinding
import com.iset.fastcolloc.ui.listings.HomeListingAdapter
import coil.load
import com.iset.fastcolloc.ui.listings.ListingBoundFragment
import com.iset.fastcolloc.viewmodel.ListingViewModel
import com.iset.fastcolloc.viewmodel.ListingViewModelFactory

class HomeFragment : ListingBoundFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {
    override val listings: ListingViewModel by viewModels { ListingViewModelFactory(requireActivity().application) }
    private var resultsRevealed = false
    override fun onViewCreated(view: View, state: Bundle?) {
        observeMessages()
        val owner = viewLifecycleOwner
        if (state != null) resultsRevealed = true
        binding.heroArt.setOnClickListener { findNavController().navigate(R.id.profileFragment) }
        vm.photoUrls.observe(viewLifecycleOwner) { urls ->
            binding.heroArt.load(urls.values.firstOrNull()) { placeholder(R.drawable.ic_fastcoloc_symbol); error(R.drawable.ic_fastcoloc_symbol); diskCachePolicy(coil.request.CachePolicy.DISABLED); memoryCachePolicy(coil.request.CachePolicy.DISABLED) }
        }
        vm.details.observe(viewLifecycleOwner) { details ->
            details?.profile?.let { profile ->
                binding.tvWelcome.text = getString(R.string.fc_home_greeting, profile.firstName.orEmpty().ifBlank { "bienvenue" })
                binding.btnContinue.isVisible = profile.completedAt == null
            }
        }
        fun catalog(filters: Boolean = false) {
            androidx.core.view.WindowCompat.getInsetsController(requireActivity().window, binding.root).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
            findNavController().navigate(R.id.listingCatalogFragment, Bundle().apply {
                putString("q", binding.etSearch.text.toString().trim())
                putString("kind", listings.query["kind"].orEmpty())
                putBoolean("showFilters", filters)
            })
        }
        binding.btnListings.setOnClickListener { catalog() }
        binding.btnSeeAll.setOnClickListener { catalog() }
        binding.btnFilters.setOnClickListener { catalog(true) }
        binding.etSearch.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { catalog(); true } else false
        }
        when (listings.query["kind"]) { "OFFER" -> binding.chipOffers.isChecked = true; "REQUEST" -> binding.chipRequests.isChecked = true }
        listOf(binding.chipAll to "", binding.chipOffers to "OFFER", binding.chipRequests to "REQUEST").forEach { (chip, kind) ->
            chip.setOnCheckedChangeListener { _, checked -> if (checked) listings.load("catalog", mapOf("sort" to "NEWEST", "kind" to kind).filterValues { it.isNotBlank() }, 0) }
        }
        binding.btnFavorites.setOnClickListener { findNavController().navigate(R.id.favoritesFragment) }
        binding.btnMine.setOnClickListener { findNavController().navigate(R.id.mineListingsFragment) }
        binding.btnPublish.setOnClickListener { (requireActivity() as AuthActivity).navigateWithLiquidPop(it, R.id.listingEditorFragment) }
        binding.btnContinue.setOnClickListener { vm.continueRegistration() }
        val adapter = HomeListingAdapter({ row -> findNavController().navigate(R.id.listingDetailFragment, Bundle().apply { putString("listingId", row.id) }) }, listings::favorite)
        binding.rvRecent.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecent.adapter = adapter
        (binding.rvRecent.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        listings.page.observe(viewLifecycleOwner) { page ->
            adapter.submitList(page?.items.orEmpty().take(6)) {
                if (!resultsRevealed && !page?.items.isNullOrEmpty()) {
                    resultsRevealed = true
                    if (owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && com.iset.fastcolloc.ui.motion.Motion.enabled(requireContext())) {
                        binding.rvRecent.alpha = 0f
                        binding.rvRecent.animate().alpha(1f).setDuration(180).start()
                    }
                }
            }
            binding.tvRecentState.text = if (page?.items.isNullOrEmpty()) "Pas encore d’annonce. Ta recherche peut commencer par une publication." else "Les dernières annonces de la communauté."
        }
        listings.busy.observe(viewLifecycleOwner) { binding.recentSkeleton.isVisible = it && adapter.itemCount == 0; binding.recentProgress.isVisible = it && adapter.itemCount > 0; binding.btnRetry.isEnabled = !it; binding.btnRetry.isVisible = !it && (adapter.itemCount == 0 || listings.offline.value == true) }
        listings.offline.observe(viewLifecycleOwner) { if (it) { binding.tvRecentState.text = "Connexion indisponible. Actualise pour retrouver les dernières annonces."; binding.btnRetry.isVisible = true } }
        binding.btnRetry.setOnClickListener { listings.load("catalog", listings.query, 0) }
        if (listings.page.value == null) listings.load("catalog", mapOf("sort" to "NEWEST"), 0)
    }
    override fun onDestroyView() { binding.rvRecent.animate().cancel(); binding.rvRecent.adapter = null; super.onDestroyView() }
}
