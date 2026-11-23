package com.iset.fastcolloc.ui.listings

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentDiscoverBinding
import com.iset.fastcolloc.viewmodel.*

class DiscoverFragment : ListingBoundFragment<FragmentDiscoverBinding>(FragmentDiscoverBinding::inflate) {
    private val discovery: DiscoveryViewModel by activityViewModels { DiscoveryViewModelFactory(requireActivity().application) }
    private var touch: ItemTouchHelper? = null
    override fun onViewCreated(view: View, state: Bundle?) {
        val adapter = ListingAdapter({ row ->
            findNavController().navigate(R.id.listingDetailFragment, Bundle().apply { putString("listingId", row.id) })
        }, { discovery.decide(true) }, discovery = true)
        binding.cards.layoutManager = LinearLayoutManager(requireContext())
        binding.cards.adapter = adapter
        touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, a: RecyclerView.ViewHolder, b: RecyclerView.ViewHolder) = false
            override fun getSwipeDirs(r: RecyclerView, holder: RecyclerView.ViewHolder): Int =
                if (discovery.state.value?.let { it.loading || it.acting } == true) 0 else super.getSwipeDirs(r, holder)
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                adapter.notifyItemChanged(holder.bindingAdapterPosition.takeIf { it >= 0 } ?: 0)
                discovery.decide(direction == ItemTouchHelper.RIGHT)
            }
        }).also { it.attachToRecyclerView(binding.cards) }
        binding.btnPass.setOnClickListener { discovery.decide(false) }
        binding.btnLike.setOnClickListener { discovery.decide(true) }
        binding.btnRetry.setOnClickListener { discovery.refresh() }
        discovery.state.observe(viewLifecycleOwner) { result ->
            val enabled = !result.loading && !result.acting && result.items.isNotEmpty()
            binding.progress.isVisible = result.loading || result.acting
            binding.btnLike.isEnabled = enabled
            binding.btnPass.isEnabled = enabled
            binding.btnRetry.isEnabled = !result.loading && !result.acting
            binding.btnRetry.isVisible = result.error != null || result.items.isEmpty()
            binding.tvState.text = result.error ?: when {
                result.acting -> "Enregistrement de ton choix…"
                result.loading -> "Chargement des annonces…"
                result.exhausted -> "Tu as parcouru les annonces disponibles. Reviens découvrir les prochaines annonces."
                else -> "À droite : favoris · À gauche : passer"
            }
            adapter.enabled = enabled
            adapter.submitList(result.items.take(1)) { if (adapter.itemCount > 0) adapter.notifyItemChanged(0, "state") }
        }
        discovery.open()
    }
    override fun onDestroyView() { touch?.attachToRecyclerView(null); touch = null; binding.cards.adapter = null; super.onDestroyView() }
}
