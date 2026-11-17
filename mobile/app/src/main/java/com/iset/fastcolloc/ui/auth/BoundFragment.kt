package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import com.iset.fastcolloc.viewmodel.Sprint1ViewModel

abstract class BoundFragment<B : ViewBinding>(private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> B) : Fragment() {
    private var _binding: B? = null
    protected val binding get() = checkNotNull(_binding)
    protected val vm: Sprint1ViewModel by activityViewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = inflate(inflater, container, false); return binding.root
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
    protected fun form(action: () -> Unit) {
        try { action() } catch (e: IllegalArgumentException) { vm.formError(e.message ?: "Vérifie les champs.") }
    }
}
