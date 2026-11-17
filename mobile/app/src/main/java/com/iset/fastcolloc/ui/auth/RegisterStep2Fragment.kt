package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.data.remote.*
import com.iset.fastcolloc.databinding.FragmentRegisterStep2Binding

class RegisterStep2Fragment : BoundFragment<FragmentRegisterStep2Binding>(FragmentRegisterStep2Binding::inflate) {
    private var countries = emptyList<CountryDto>(); private var governors = emptyList<PlaceDto>(); private var places = emptyList<PlaceDto>()
    private var countryCode = "TN"; private var governorId: Long? = null; private var localityId: Long? = null
    override fun onViewCreated(view: View, state: Bundle?) {
        val a = vm.details.value?.address
        countryCode = a?.string("country_code") ?: "TN"; governorId = a?.long("governorate_id"); localityId = a?.long("locality_id")
        if(state == null) { binding.etStreet.setText(a?.string("street")); binding.etPostalCode.setText(a?.string("postal_code")) }
        vm.countries.observe(viewLifecycleOwner) { countries = it; fill(binding.spCountry, it.map { c -> c.name }, it.indexOfFirst { c -> c.code == countryCode }) { pos ->
            val next = countries.getOrNull(pos)?.code ?: return@fill
            if(next != countryCode) { countryCode = next; governorId = null; localityId = null; vm.country(next) }
        } }
        vm.governorates.observe(viewLifecycleOwner) { governors = it; fill(binding.spGovernorate, it.map { p -> p.name }, it.indexOfFirst { p -> p.id == governorId }) { pos ->
            val next = governors.getOrNull(pos)?.id
            if(next != governorId) { governorId = next; localityId = null; if(next != null) vm.governorate(next) }
        } }
        vm.localities.observe(viewLifecycleOwner) { places = it; fill(binding.spLocality, it.map { p -> p.name }, it.indexOfFirst { p -> p.id == localityId }) { pos -> localityId = places.getOrNull(pos)?.id } }
        binding.btnNext.setOnClickListener { form { vm.saveAddress(GeographyDto(countryCode, governorId ?: 0, localityId ?: 0, binding.etStreet.text.toString().trim(), binding.etPostalCode.text.toString().trim())) } }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        vm.busy.observe(viewLifecycleOwner) { binding.btnNext.isEnabled = !it; binding.btnBack.isEnabled = !it }
        vm.geography()
    }
    private fun fill(spinner: Spinner, names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        spinner.onItemSelectedListener = null
        spinner.adapter = ArrayAdapter(requireContext(), com.iset.fastcolloc.R.layout.item_fc_dropdown, listOf("Choisir…") + names)
        spinner.setSelection(selected + 1, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { onSelect(position - 1) }
        }
    }
}
