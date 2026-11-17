package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import com.iset.fastcolloc.databinding.FragmentResetBinding

class ResetFragment : BoundFragment<FragmentResetBinding>(FragmentResetBinding::inflate) {
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.btnSave.setOnClickListener {
            vm.reset(binding.etPassword.text.toString(), binding.etConfirm.text.toString())
            binding.etPassword.text?.clear(); binding.etConfirm.text?.clear()
        }
        vm.busy.observe(viewLifecycleOwner) { binding.btnSave.isEnabled = !it && vm.recovery }
    }
}
