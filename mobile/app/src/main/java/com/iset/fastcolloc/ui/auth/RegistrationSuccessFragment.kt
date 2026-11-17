package com.iset.fastcolloc.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import com.iset.fastcolloc.R
import com.iset.fastcolloc.ui.MainActivity

class RegistrationSuccessFragment : androidx.fragment.app.Fragment(R.layout.fragment_registration_success) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialButton>(R.id.btnGoToApp).setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }
    }
}
