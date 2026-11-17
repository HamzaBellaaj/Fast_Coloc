package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.data.remote.ContactsDto
import com.iset.fastcolloc.databinding.FragmentRegisterStep4Binding

class RegisterStep4Fragment : BoundFragment<FragmentRegisterStep4Binding>(FragmentRegisterStep4Binding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        val c = vm.details.value?.contacts
        if(state == null) {
            binding.etMobile.setText(c?.string("mobile_phone")); binding.etResidential.setText(c?.string("residential_phone")); binding.etAvailability.setText(c?.string("call_availability"))
            binding.etMsn.setText(c?.string("msn_contact")); binding.etSkype.setText(c?.string("skype_contact")); binding.etWebsite.setText(c?.string("website_url"))
        }
        binding.btnNext.setOnClickListener { vm.saveContacts(ContactsDto(binding.etMobile.text.toString(), binding.etResidential.text.toString(), binding.etAvailability.text.toString(), binding.etMsn.text.toString(), binding.etSkype.text.toString(), binding.etWebsite.text.toString())) }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        vm.busy.observe(viewLifecycleOwner) { binding.btnNext.isEnabled = !it; binding.btnBack.isEnabled = !it }
    }
}
