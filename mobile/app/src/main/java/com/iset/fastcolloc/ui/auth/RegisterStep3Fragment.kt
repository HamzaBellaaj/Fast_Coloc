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
        binding.spSmoking.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Non renseigné", "Non-fumeur", "Fumeur"))
        if (state == null) {
            binding.etActivities.setText(p?.list("main_activities")); binding.etHobbies.setText(p?.list("hobbies"))
            binding.etNationality.setText(p?.string("nationality_code")); binding.etLanguages.setText(p?.list("spoken_languages"))
            binding.etReligion.setText(p?.string("religion")); binding.etCleanliness.setText(p?.string("cleanliness")); binding.etBiography.setText(p?.string("biography"))
            binding.spSmoking.setSelection(when(p?.string("smoking")) { "true" -> 2; "false" -> 1; else -> 0 })
        }
        binding.btnNext.setOnClickListener { form {
            vm.savePersonal(PersonalDto(splitValues(binding.etActivities.text.toString()), splitValues(binding.etHobbies.text.toString()), binding.etNationality.text.toString().uppercase().ifBlank { null }, splitValues(binding.etLanguages.text.toString()), binding.etReligion.text.toString().ifBlank { null }, when(binding.spSmoking.selectedItemPosition) { 1 -> false; 2 -> true; else -> null }, binding.etCleanliness.text.toString().ifBlank { null }, binding.etBiography.text.toString().ifBlank { null }))
        } }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        vm.busy.observe(viewLifecycleOwner) { binding.btnNext.isEnabled = !it; binding.btnBack.isEnabled = !it }
    }
}
