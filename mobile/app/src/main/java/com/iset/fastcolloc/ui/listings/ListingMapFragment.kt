package com.iset.fastcolloc.ui.listings

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentListingMapBinding
import com.iset.fastcolloc.viewmodel.*
import org.osmdroid.util.*
import org.osmdroid.views.overlay.Marker

class ListingMapFragment : ListingBoundFragment<FragmentListingMapBinding>(FragmentListingMapBinding::inflate) {
    private val mapState: MapViewModel by viewModels { MapViewModelFactory(requireActivity().application) }
    private var selectedId: String? = null
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.query.setText(mapState.filters["q"].orEmpty())
        binding.btnDetails.setOnClickListener { selectedId?.let { id ->
            findNavController().navigate(R.id.listingDetailFragment, Bundle().apply { putString("listingId", id) })
        } }
        binding.btnSearch.setOnClickListener { mapState.load(mapState.filters + ("q" to binding.query.text.toString().trim())) }
        mapState.state.observe(viewLifecycleOwner) { result ->
            binding.progress.isVisible = result.loading
            binding.btnSearch.isEnabled = !result.loading
            binding.tvState.text = result.error ?: when {
                result.loading -> "Chargement des annonces…"
                result.items.isEmpty() -> "Aucune offre géolocalisée pour ces filtres. Modifie la recherche."
                else -> "${result.items.size} offre(s) · touche un marqueur pour consulter la fiche."
            }
            binding.map.overlays.removeAll { it is Marker }
            selectedId = null
            binding.btnDetails.isVisible = false
            binding.tvSelection.text = ""
            val points = result.items.map { row ->
                val point = GeoPoint(row.latitude!!, row.longitude!!)
                binding.map.overlays.add(Marker(binding.map).apply {
                    position = point
                    title = row.title
                    snippet = "${row.locality} · ${row.rent} TND / mois"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { marker, _ ->
                        selectedId = row.id
                        binding.tvSelection.text = "${row.title}\n${row.locality} · ${row.rent} TND / mois"
                        binding.btnDetails.isVisible = true
                        marker.showInfoWindow()
                        true
                    }
                })
                point
            }
            if (points.isEmpty()) {
                val center = result.center
                binding.map.controller.setCenter(if (center == null) GeoPoint(34.1, 9.6) else GeoPoint(center.latitude,center.longitude))
                binding.map.controller.setZoom(if (center == null) 6.0 else 12.0)
            }
            else {
                val lifecycle = viewLifecycleOwner.lifecycle
                binding.map.post {
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    if (points.size == 1) { binding.map.controller.setCenter(points.first()); binding.map.controller.setZoom(14.0) }
                    else binding.map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 48)
                }
                }
            }
            binding.map.invalidate()
        }
        if (mapState.state.value?.loaded != true) {
            val filters = arguments?.getString("filters")?.let { raw ->
                runCatching { org.json.JSONObject(raw).let { json -> json.keys().asSequence().associateWith { json.getString(it) } } }.getOrNull()
            }.orEmpty()
            binding.query.setText(filters["q"].orEmpty())
            mapState.load(filters)
        }
    }
    override fun onResume() { super.onResume(); binding.map.onResume() }
    override fun onPause() { binding.map.onPause(); super.onPause() }
    override fun onDestroyView() { binding.map.onDetach(); super.onDestroyView() }
}
