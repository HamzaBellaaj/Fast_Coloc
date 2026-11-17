package com.iset.fastcolloc.ui.auth

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.iset.fastcolloc.databinding.FragmentRegisterStep5Binding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RegisterStep5Fragment : BoundFragment<FragmentRegisterStep5Binding>(FragmentRegisterStep5Binding::inflate) {
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri != null && view != null) {
            binding.ivPreview.isVisible = true; binding.ivPreview.load(uri)
            vm.upload(uri, binding.etCaption.text.toString())
        }
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        val adapter = PhotoAdapter(vm::caption) { id -> AlertDialog.Builder(requireContext())
            .setMessage("Supprimer cette photo privée ? Il faudra revalider l’étape 5.")
            .setNegativeButton("Annuler", null).setPositiveButton("Supprimer") { _, _ -> vm.deletePhoto(id) }.show() }
        binding.rvPhotos.layoutManager = GridLayoutManager(requireContext(), 2); binding.rvPhotos.adapter = adapter
        fun render() { adapter.submitList(vm.details.value?.photos.orEmpty().map { PhotoItem(it, vm.photoUrls.value?.get(it.id)) }) }
        vm.details.observe(viewLifecycleOwner) { render() }; vm.photoUrls.observe(viewLifecycleOwner) { render() }
        vm.busy.observe(viewLifecycleOwner) {
            adapter.busy = it; binding.btnNext.isEnabled = !it; binding.btnBack.isEnabled = !it
            binding.btnAddPhoto.isEnabled = !it && vm.details.value?.photos.orEmpty().size < 5
            if(!it) { binding.ivPreview.isVisible = false; binding.ivPreview.setImageDrawable(null) }
        }
        binding.btnAddPhoto.setOnClickListener { picker.launch("image/*") }
        binding.btnNext.setOnClickListener { vm.complete() }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.photos(); while(true) { delay(45000); vm.photos() }
        } }
    }
    override fun onDestroyView() { binding.rvPhotos.adapter = null; super.onDestroyView() }
}
