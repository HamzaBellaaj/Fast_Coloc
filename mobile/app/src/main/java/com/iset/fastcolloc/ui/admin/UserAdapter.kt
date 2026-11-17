package com.iset.fastcolloc.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.iset.fastcolloc.R
import com.iset.fastcolloc.model.AdminUser

class UserAdapter(
    private val onEdit: (AdminUser) -> Unit,
    private val onBlock: (AdminUser) -> Unit,
    private val onDelete: (AdminUser) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {
    private val items = mutableListOf<AdminUser>()

    fun submitList(users: List<AdminUser>) {
        items.clear()
        items.addAll(users)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tvAdminUserName)
        private val email: TextView = itemView.findViewById(R.id.tvAdminUserEmail)
        private val phone: TextView = itemView.findViewById(R.id.tvAdminUserPhone)
        private val role: TextView = itemView.findViewById(R.id.tvAdminUserRole)
        private val status: TextView = itemView.findViewById(R.id.tvAdminUserStatus)
        private val edit: MaterialButton = itemView.findViewById(R.id.btnEditUser)
        private val block: MaterialButton = itemView.findViewById(R.id.btnBlockUser)
        private val delete: MaterialButton = itemView.findViewById(R.id.btnDeleteUser)

        fun bind(user: AdminUser) {
            name.text = user.fullName
            email.text = user.email
            phone.text = if (user.phone.isBlank()) "Téléphone non renseigné" else user.phone
            role.text = if (user.collocationRole == "SEEKER") "Demandeur" else "Offreur"
            status.text = if (user.isBlocked) "Compte bloqué" else "Compte actif"
            status.setTextColor(itemView.context.getColor(
                if (user.isBlocked) android.R.color.holo_red_dark else android.R.color.holo_green_dark
            ))
            block.text = if (user.isBlocked) "Débloquer" else "Bloquer"
            edit.setOnClickListener { onEdit(user) }
            block.setOnClickListener { onBlock(user) }
            delete.setOnClickListener { onDelete(user) }
        }
    }
}
