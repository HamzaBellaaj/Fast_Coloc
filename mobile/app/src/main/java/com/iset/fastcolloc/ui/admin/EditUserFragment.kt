package com.iset.fastcolloc.ui.admin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.iset.fastcolloc.R
import com.iset.fastcolloc.viewmodel.AdminViewModel
import com.iset.fastcolloc.viewmodel.AdminViewModelFactory

class EditUserFragment : Fragment(R.layout.fragment_edit_user) {
    private val viewModel: AdminViewModel by activityViewModels {
        AdminViewModelFactory(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val userId = requireArguments().getInt(ARG_USER_ID)
        val firstName = view.findViewById<TextInputEditText>(R.id.etEditFirstName)
        val lastName = view.findViewById<TextInputEditText>(R.id.etEditLastName)
        val email = view.findViewById<TextInputEditText>(R.id.etEditEmail)
        val phone = view.findViewById<TextInputEditText>(R.id.etEditPhone)

        viewModel.users.observe(viewLifecycleOwner) { users ->
            val user = users.firstOrNull { it.id == userId } ?: return@observe
            if (firstName.tag != "loaded") {
                firstName.setText(user.firstName)
                lastName.setText(user.lastName)
                email.setText(user.email)
                phone.setText(user.phone)
                firstName.tag = "loaded"
            }
        }

        view.findViewById<MaterialButton>(R.id.btnSaveUserChanges).setOnClickListener {
            viewModel.updateUser(
                userId,
                firstName.text?.toString().orEmpty(),
                lastName.text?.toString().orEmpty(),
                email.text?.toString().orEmpty(),
                phone.text?.toString().orEmpty()
            )
        }
        view.findViewById<MaterialButton>(R.id.btnCancelUserChanges).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
                if (message == "Informations mises à jour.") parentFragmentManager.popBackStack()
                viewModel.clearMessage()
            }
        }
    }

    companion object {
        private const val ARG_USER_ID = "user_id"
        fun newInstance(userId: Int) = EditUserFragment().apply {
            arguments = Bundle().apply { putInt(ARG_USER_ID, userId) }
        }
    }
}
