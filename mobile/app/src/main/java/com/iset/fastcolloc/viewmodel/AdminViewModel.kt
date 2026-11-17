package com.iset.fastcolloc.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.iset.fastcolloc.data.AdminRepository
import com.iset.fastcolloc.model.AdminUser
import kotlinx.coroutines.launch

class AdminViewModel(private val repository: AdminRepository) : ViewModel() {
    val users: LiveData<List<AdminUser>> = repository.observeUsers().asLiveData()

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun blockUser(userId: Int, blocked: Boolean) = runAction {
        repository.setBlocked(userId, blocked)
        if (blocked) "Compte bloqué." else "Compte débloqué."
    }

    fun deleteUser(userId: Int) = runAction {
        repository.deleteUser(userId)
        "Utilisateur supprimé."
    }

    fun updateUser(userId: Int, firstName: String, lastName: String, email: String, phone: String) = runAction {
        repository.updateUser(userId, firstName, lastName, email, phone)
        "Informations mises à jour."
    }

    fun clearMessage() { _message.value = null }

    private fun runAction(action: suspend () -> String) {
        viewModelScope.launch {
            _message.value = try {
                action()
            } catch (e: Exception) {
                e.message ?: "Une erreur est survenue."
            }
        }
    }
}
