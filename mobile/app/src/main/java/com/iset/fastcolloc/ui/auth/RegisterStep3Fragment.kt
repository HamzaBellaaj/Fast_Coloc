package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.data.remote.PersonalDto
import com.iset.fastcolloc.databinding.FragmentRegisterStep3Binding

class RegisterStep3Fragment : BoundFragment<FragmentRegisterStep3Binding>(FragmentRegisterStep3Binding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        val p = vm.details.value?.personal
        binding.spSmoking.adapter = ArrayAdapter(requireContext(), com.iset.fastcolloc.R.layout.item_fc_dropdown, listOf("Non renseigné", "Non-fumeur", "Fumeur"))
        vm.countries.observe(viewLifecycleOwner) { countries ->
            val current = binding.etNationality.text.toString()
            val labels = listOf(getString(com.iset.fastcolloc.R.string.profile_nationality_unspecified)) + countries.map { it.name }
            binding.etNationality.setAdapter(ArrayAdapter(requireContext(), com.iset.fastcolloc.R.layout.item_fc_dropdown, labels))
            val selected = countries.find { it.code == current }
            if (selected != null) binding.etNationality.setText(selected.name, false)
        }
        if (state == null) {
            binding.etActivities.setText(p?.list("main_activities")); binding.etHobbies.setText(p?.list("hobbies"))
            val nationalityCode = p?.string("nationality_code")
            binding.etNationality.setText(vm.countries.value.orEmpty().find { it.code == nationalityCode }?.name ?: nationalityCode.orEmpty(), false)
            binding.etLanguages.setText(p?.list("spoken_languages"))
            binding.etReligion.setText(p?.string("religion")); binding.etCleanliness.setText(p?.string("cleanliness").orEmpty(), false); binding.etBiography.setText(p?.string("biography"))
            binding.spSmoking.setSelection(when(p?.string("smoking")) { "true" -> 2; "false" -> 1; else -> 0 })
        }
        vm.loadCountries()
        binding.btnNext.setOnClickListener { form {
            val nationality = binding.etNationality.text.toString()
            val nationalityCode = if (nationality.isBlank() || nationality == getString(com.iset.fastcolloc.R.string.profile_nationality_unspecified)) null
                else vm.countries.value.orEmpty().find { it.name == nationality || it.code == nationality }?.code
                    ?: throw IllegalArgumentException("Choisis une nationalité dans la liste des pays.")
            vm.savePersonal(PersonalDto(splitValues(binding.etActivities.text.toString()), splitValues(binding.etHobbies.text.toString()), nationalityCode, splitValues(binding.etLanguages.text.toString()), binding.etReligion.text.toString().ifBlank { null }, when(binding.spSmoking.selectedItemPosition) { 1 -> false; 2 -> true; else -> null }, binding.etCleanliness.text.toString().takeUnless { it.isBlank() || it == getString(com.iset.fastcolloc.R.string.profile_no_answer) }, binding.etBiography.text.toString().ifBlank { null }))
        } }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        vm.busy.observe(viewLifecycleOwner) { binding.btnNext.isEnabled = !it; binding.btnBack.isEnabled = !it }
    }
}
