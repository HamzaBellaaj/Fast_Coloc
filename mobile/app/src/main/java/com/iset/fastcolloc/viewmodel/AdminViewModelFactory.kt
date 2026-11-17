package com.iset.fastcolloc.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.iset.fastcolloc.data.AdminRepository
import com.iset.fastcolloc.data.SessionManager
import com.iset.fastcolloc.data.local.AppDatabase

class AdminViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminViewModel::class.java)) {
            val repository = AdminRepository(
                AppDatabase.getDatabase(appContext).userDao(),
                SessionManager(appContext)
            )
            return AdminViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
