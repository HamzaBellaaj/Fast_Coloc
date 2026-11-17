package com.iset.fastcolloc.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.iset.fastcolloc.R
import com.iset.fastcolloc.model.AdminUser
import com.iset.fastcolloc.viewmodel.AdminViewModel
import com.iset.fastcolloc.viewmodel.AdminViewModelFactory

class AdminUsersFragment : Fragment(R.layout.fragment_admin_users) {
    private val viewModel: AdminViewModel by activityViewModels {
        AdminViewModelFactory(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.rvAdminUsers)
        val empty = view.findViewById<TextView>(R.id.tvNoAdminUsers)
        val adapter = UserAdapter(
            onEdit = ::openEdit,
            onBlock = { user -> viewModel.blockUser(user.id, !user.isBlocked) },
            onDelete = ::confirmDelete
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        viewModel.users.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users)
            empty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun openEdit(user: AdminUser) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.mainContent, EditUserFragment.newInstance(user.id))
            .addToBackStack("edit_user")
            .commit()
    }

    private fun confirmDelete(user: AdminUser) {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer le compte")
            .setMessage("Voulez-vous vraiment supprimer le compte de ${user.fullName} ? Cette action est définitive.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ -> viewModel.deleteUser(user.id) }
            .show()
    }
}
