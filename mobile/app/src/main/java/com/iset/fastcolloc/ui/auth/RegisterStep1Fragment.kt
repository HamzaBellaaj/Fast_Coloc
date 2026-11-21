package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.data.remote.IdentityDto
import com.iset.fastcolloc.databinding.FragmentRegisterStep1Binding

class RegisterStep1Fragment : BoundFragment<FragmentRegisterStep1Binding>(FragmentRegisterStep1Binding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        val profile = vm.details.value?.profile
        if (state == null) {
            binding.etFirstName.setText(profile?.firstName); binding.etLastName.setText(profile?.lastName)
            binding.etAge.setText(profile?.age?.toString()); binding.etGender.setText(profile?.gender.orEmpty(), false)
        }
        val signedIn = profile != null
        listOf(binding.etEmail, binding.etPassword, binding.etConfirmPassword).forEach { field ->
            (field.parent.parent as View).isVisible = !signedIn
            val content = (field.parent.parent as View).parent as android.view.ViewGroup
            for(index in 0 until content.childCount) {
                val label = content.getChildAt(index)
                if(label is android.widget.TextView && label.labelFor == field.id) label.isVisible = !signedIn
            }
        }
        binding.btnNext.setOnClickListener { form {
            val identity = IdentityDto(binding.etFirstName.text.toString().trim(), binding.etLastName.text.toString().trim(), binding.etGender.text.toString().takeUnless { it.isBlank() || it == getString(com.iset.fastcolloc.R.string.profile_no_answer) }, binding.etAge.text.toString().toIntOrNull() ?: throw IllegalArgumentException("Saisis un âge valide."))
            if (signedIn) vm.saveIdentity(identity) else {
                vm.signup(binding.etEmail.text.toString(), binding.etPassword.text.toString(), binding.etConfirmPassword.text.toString(), identity)
                binding.etPassword.text?.clear(); binding.etConfirmPassword.text?.clear()
            }
        } }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        vm.busy.observe(viewLifecycleOwner) { binding.btnNext.isEnabled = !it; binding.btnBack.isEnabled = !it }
    }
}
