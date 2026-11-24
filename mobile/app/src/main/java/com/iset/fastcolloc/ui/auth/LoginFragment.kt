package com.iset.fastcolloc.ui.auth
import com.iset.fastcolloc.ui.showBusy

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentLoginBinding

class LoginFragment : BoundFragment<FragmentLoginBinding>(FragmentLoginBinding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.btnLogin.setOnClickListener {
            val password = binding.etLoginPassword.text.toString()
            vm.login(binding.etLoginEmail.text.toString(), password)
            binding.etLoginPassword.text?.clear()
        }
        binding.btnOpenRegister.setOnClickListener { vm.editing = false; findNavController().navigate(R.id.action_login_to_register1) }
        binding.btnGoogle.setOnClickListener { vm.google() }
        binding.btnForgot.setOnClickListener { findNavController().navigate(R.id.forgotFragment) }
        vm.busy.observe(viewLifecycleOwner) { busy -> binding.btnLogin.showBusy(busy); binding.btnLogin.isEnabled = !busy; binding.btnGoogle.isEnabled = !busy; binding.btnOpenRegister.isEnabled = !busy; binding.btnForgot.isEnabled = !busy }
    }
}
