package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentEmailBinding

class EmailFragment : BoundFragment<FragmentEmailBinding>(FragmentEmailBinding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        val forgot = findNavController().currentDestination?.id == R.id.forgotFragment
        if(state == null) binding.etEmail.setText(vm.pendingEmail)
        if(forgot) binding.tvExplanation.text = getString(R.string.s1_recovery_help)
        binding.btnSend.setText(if(forgot) R.string.s1_send_recovery else R.string.s1_btnSend)
        binding.btnSend.setOnClickListener { if(forgot) vm.recover(binding.etEmail.text.toString()) else vm.resend(binding.etEmail.text.toString()) }
        binding.btnLogin.setOnClickListener { findNavController().popBackStack(R.id.loginFragment, false) }
        vm.busy.observe(viewLifecycleOwner) { binding.btnSend.isEnabled = !it; binding.btnLogin.isEnabled = !it }
    }
}
