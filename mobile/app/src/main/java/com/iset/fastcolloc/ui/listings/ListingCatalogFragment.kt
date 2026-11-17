package com.iset.fastcolloc.ui.listings

import android.os.Bundle
import androidx.fragment.app.viewModels
import com.iset.fastcolloc.ui.auth.calendarField
import android.view.*
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.*

class ListingCatalogFragment :
    ListingBoundFragment<FragmentListingCatalogBinding>(FragmentListingCatalogBinding::inflate) {
    override val listings: com.iset.fastcolloc.viewmodel.ListingViewModel by viewModels {
        com.iset.fastcolloc.viewmodel.ListingViewModelFactory(requireActivity().application)
    }
    private lateinit var adapter: ListingAdapter

    override fun onViewCreated(view: View, state: Bundle?) {
        observeMessages()
        adapter =
            ListingAdapter(
                { row ->
                    findNavController()
                        .navigate(
                            R.id.listingDetailFragment,
                            Bundle().apply { putString("listingId", row.id) },
                        )
                },
                listings::favorite,
            )
        binding.rvListings.layoutManager =
            if (resources.configuration.screenWidthDp >= 600) GridLayoutManager(requireContext(), 2)
            else LinearLayoutManager(requireContext())
        binding.rvListings.setHasFixedSize(true)
        binding.rvListings.adapter = adapter
        binding.btnNewListing.setOnClickListener {
            listings.beginEditor(null)
            findNavController().navigate(R.id.listingEditorFragment)
        }
        binding.btnPrevious.setOnClickListener {
            listings.load(page = (listings.pageNumber - 1).coerceAtLeast(0))
        }
        binding.btnNextPage.setOnClickListener { listings.load(page = listings.pageNumber + 1) }
        listings.page.observe(viewLifecycleOwner) { page ->
            adapter.submitList(page?.items.orEmpty())
            render()
        }
        listings.offline.observe(viewLifecycleOwner) { render() }
        listings.busy.observe(viewLifecycleOwner) { render() }
        (requireActivity() as MenuHost).addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                    inflater.inflate(R.menu.menu_listings, menu)
                    (menu.findItem(R.id.s2Search).actionView as SearchView).apply {
                        queryHint = "Ville, quartier, mots-clés…"
                        setQuery(listings.query["q"].orEmpty(), false)
                        setOnQueryTextListener(
                            object : SearchView.OnQueryTextListener {
                                override fun onQueryTextSubmit(q: String?): Boolean {
                                    listings.load(
                                        query = listings.query + ("q" to q.orEmpty().trim()),
                                        page = 0,
                                    )
                                    clearFocus()
                                    return true
                                }

                                override fun onQueryTextChange(q: String?) = false
                            }
                        )
                    }
                }

                override fun onMenuItemSelected(item: MenuItem): Boolean {
                    when (item.itemId) {
                        R.id.s2Filters -> filters()
                        R.id.s2Refresh -> listings.load()
                        else -> return false
                    }
                    return true
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
        listings.loadAreas()
        listings.areas.observe(viewLifecycleOwner) { areas ->
            if (areas.isNotEmpty() && arguments?.getBoolean("showFilters") == true) {
                arguments?.remove("showFilters")
                filters()
            }
        }
        if (listings.page.value == null) listings.load(
            mode = arguments?.getString("mode") ?: "catalog",
            query = listOf("q", "kind").mapNotNull { key -> arguments?.getString(key)?.takeIf { it.isNotBlank() }?.let { key to it } }.toMap()
        )
    }

    private fun render() {
        val page = listings.page.value
        val busy = listings.busy.value == true
        val offline = listings.offline.value == true
        binding.tvTitle.text =
            when (listings.mode) {
                "mine" -> getString(R.string.s2_mine)
                "favorites" -> getString(R.string.s2_favorites)
                else -> "Trouve ta coloc."
            }
        binding.listingProgress.isVisible = busy
        binding.tvState.text =
            when {
                busy -> "Actualisation en cours…"
                offline ->
                    "Hors ligne · copie locale du ${listings.snapshot.value?.savedAt?.let{java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}?:"cache indisponible"}. Actualise pour vérifier."
                page == null || page.items.isEmpty() ->
                    "Aucune annonce à afficher pour ces critères."
                else -> "${page.total} résultat(s) · page ${page.page+1}"
            }
        (binding.btnPrevious.parent as View).isVisible = page != null && page.total > page.size
        binding.btnPrevious.isEnabled = !busy && listings.pageNumber > 0
        binding.btnNextPage.isEnabled =
            !busy && page != null && (page.page + 1) * page.size < page.total
        binding.btnNewListing.isEnabled = !busy && !offline
        adapter.enabled = !busy && !offline
        // Rebind action buttons when connectivity/busy state changes, without replacing server
        // data.
        if (adapter.itemCount > 0) adapter.notifyItemRangeChanged(0, adapter.itemCount, "state")
    }

    private fun filters() {
        val b = DialogListingFiltersBinding.inflate(layoutInflater)
        fun options(sp: android.widget.Spinner, values: List<String>) {
            sp.adapter =
                ArrayAdapter(
                    requireContext(),
                    com.iset.fastcolloc.R.layout.item_fc_dropdown,
                    values,
                )
        }
        options(b.spKind, listOf("Offres et demandes", "Offres", "Demandes"))
        b.spKind.setSelection(
            listOf("", "OFFER", "REQUEST")
                .indexOf(listings.query["kind"].orEmpty())
                .coerceAtLeast(0)
        )
        val areas = listings.areas.value.orEmpty()
        val regions = areas.distinctBy { it.governorateId }
        options(b.spRegion, listOf("Toute la Tunisie") + regions.map { it.governorate })
        b.spRegion.setSelection(
            (regions.indexOfFirst {
                    it.governorateId.toString() == listings.query["governorateId"]
                } + 1)
                .coerceAtLeast(0)
        )
        var towns =
            areas.filter {
                b.spRegion.selectedItemPosition == 0 ||
                    it.governorateId ==
                        regions.getOrNull(b.spRegion.selectedItemPosition - 1)?.governorateId
            }
        fun showTowns() {
            towns =
                areas.filter {
                    b.spRegion.selectedItemPosition == 0 ||
                        it.governorateId ==
                            regions.getOrNull(b.spRegion.selectedItemPosition - 1)?.governorateId
                }
            options(b.spLocality, listOf("Toutes les villes") + towns.map { it.locality })
            b.spLocality.setSelection(
                (towns.indexOfFirst { it.id.toString() == listings.query["localityId"] } + 1)
                    .coerceAtLeast(0)
            )
        }
        showTowns()
        b.spRegion.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p: android.widget.AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long,
                ) {
                    showTowns()
                }

                override fun onNothingSelected(p: android.widget.AdapterView<*>?) = Unit
            }
        val fields =
            mapOf(
                "minRent" to b.etMinRent,
                "maxRent" to b.etMaxRent,
                "minCapacity" to b.etMinCapacity,
                "propertyType" to b.etPropertyFilter,
                "roommateType" to b.etRoommateFilter,
                "equipment" to b.etEquipmentFilter,
                "availableOn" to b.etAvailableFilter,
            )
        fields.forEach { (key, field) -> field.setText(listings.query[key].orEmpty()) }
        calendarField(b.etAvailableFilter, "Disponible le", optional = true)
        options(b.spSort, listOf("Plus récentes", "Loyer croissant", "Loyer décroissant"))
        b.spSort.setSelection(
            listOf("NEWEST", "RENT_ASC", "RENT_DESC")
                .indexOf(listings.query["sort"] ?: "NEWEST")
                .coerceAtLeast(0)
        )
        val dialog =
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.s2_filters)
                .setView(b.root)
                .setNegativeButton("Annuler", null)
                .setNeutralButton("Réinitialiser") { _, _ ->
                    listings.load(query = emptyMap(), page = 0)
                }
                .setPositiveButton("Rechercher", null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = listings.query.toMutableMap()
                fields.forEach { (key, field) -> query[key] = field.text.toString().trim() }
                query["kind"] = listOf("", "OFFER", "REQUEST")[b.spKind.selectedItemPosition]
                query["sort"] =
                    listOf("NEWEST", "RENT_ASC", "RENT_DESC")[b.spSort.selectedItemPosition]
                query["governorateId"] =
                    regions
                        .getOrNull(b.spRegion.selectedItemPosition - 1)
                        ?.governorateId
                        ?.toString()
                        .orEmpty()
                query["localityId"] =
                    towns.getOrNull(b.spLocality.selectedItemPosition - 1)?.id?.toString().orEmpty()
                val low = query["minRent"]?.toDoubleOrNull()
                val high = query["maxRent"]?.toDoubleOrNull()
                if (low != null && high != null && low > high) {
                    b.etMaxRent.error = "Le maximum doit dépasser le minimum."
                    return@setOnClickListener
                }
                listings.load(query = query.filterValues { it.isNotBlank() }, page = 0)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        binding.rvListings.adapter = null
        super.onDestroyView()
    }
}
