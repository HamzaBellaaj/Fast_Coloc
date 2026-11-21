package com.iset.fastcolloc.ui.listings

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentRecommendationsBinding
import com.iset.fastcolloc.viewmodel.*

class RecommendationsFragment : ListingBoundFragment<FragmentRecommendationsBinding>(FragmentRecommendationsBinding::inflate) {
    private val recommendations: RecommendationViewModel by viewModels {
        RecommendationViewModelFactory(requireActivity().application)
    }
    private lateinit var adapter: ListingAdapter
    override fun onViewCreated(view: View, state: Bundle?) {
        observeMessages()
        adapter = ListingAdapter(
            { row -> findNavController().navigate(R.id.listingDetailFragment, Bundle().apply { putString("listingId", row.id) }) },
            recommendations::favorite,
            { row -> recommendations.state.value?.items?.find { it.listing.id == row.id }?.let { it.score to it.raison } },
        )
        binding.rvRecommendations.layoutManager = if (resources.configuration.screenWidthDp >= 600)
            GridLayoutManager(requireContext(), 2) else LinearLayoutManager(requireContext())
        binding.rvRecommendations.adapter = adapter
        binding.btnRefreshRecommendations.setOnClickListener { recommendations.refresh() }
        binding.btnCompleteRecommendationProfile.setOnClickListener { findNavController().navigate(R.id.profileFragment) }
        val owner = viewLifecycleOwner
        recommendations.state.observe(viewLifecycleOwner) { result ->
            binding.recommendationProgress.isVisible = result.loading
            binding.btnRefreshRecommendations.isEnabled = !result.loading && result.pending.isEmpty()
            binding.btnCompleteRecommendationProfile.isVisible = result.incomplete && !result.loading
            binding.tvRecommendationHint.isVisible = result.incomplete && !result.loading
            binding.tvRecommendationState.text = when {
                result.loading -> getString(R.string.recommendations_loading)
                result.error != null -> result.error
                !result.loaded -> getString(R.string.recommendations_intro)
                result.items.isEmpty() -> getString(R.string.recommendations_empty)
                else -> getString(R.string.recommendations_count, result.items.size)
            }
            adapter.enabled = !result.loading && result.pending.isEmpty()
            adapter.submitList(result.items.map { it.listing }) {
                if (owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && adapter.itemCount > 0)
                    adapter.notifyItemRangeChanged(0, adapter.itemCount, "recommendations")
            }
        }
        // Returning from details updates favorites without triggering another AI calculation.
        listings.selected.observe(viewLifecycleOwner) { row -> row?.let(recommendations::syncFavorite) }
        recommendations.event.observe(viewLifecycleOwner) { event -> event.consume()?.let {
            findNavController().navigate(R.id.loginFragment, null, NavOptions.Builder().setPopUpTo(R.id.homeFragment, true).build())
        } }
        recommendations.open()
    }
    override fun onDestroyView() {
        binding.rvRecommendations.adapter = null
        super.onDestroyView()
    }
}
