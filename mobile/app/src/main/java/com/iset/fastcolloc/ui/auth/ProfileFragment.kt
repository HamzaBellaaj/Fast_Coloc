package com.iset.fastcolloc.ui.auth
import androidx.navigation.fragment.findNavController

import android.os.Bundle
import androidx.core.view.isVisible
import coil.load
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.iset.fastcolloc.data.remote.PreferencesDto
import com.iset.fastcolloc.databinding.FragmentProfileBinding

class ProfileFragment : BoundFragment<FragmentProfileBinding>(FragmentProfileBinding::inflate) {
    private var localities = mutableSetOf<Long>()
    private var owner: String? = null
    override fun onViewCreated(view: View, state: Bundle?) {
        calendarField(binding.etMoveIn, "Date d’emménagement", optional = true)
        binding.btnDetails.setOnClickListener { binding.tvDetails.isVisible = !binding.tvDetails.isVisible; binding.btnDetails.text = if (binding.tvDetails.isVisible) "Masquer les informations" else "Toutes mes informations" }
        binding.btnShowEdit.setOnClickListener { binding.editSection.isVisible = !binding.editSection.isVisible }
        binding.btnShowPreferences.setOnClickListener { binding.preferencesSection.isVisible = !binding.preferencesSection.isVisible }
        vm.photoUrls.observe(viewLifecycleOwner) { urls ->
            val photo = urls.values.firstOrNull()
            val padding = if (photo == null) (64 * resources.displayMetrics.density).toInt() else 0
            binding.ivProfileAvatar.setPadding(padding, padding, padding, padding)
            binding.ivProfileAvatar.scaleType = if (photo == null) android.widget.ImageView.ScaleType.CENTER_INSIDE else android.widget.ImageView.ScaleType.CENTER_CROP
            binding.ivProfileAvatar.load(photo) { placeholder(com.iset.fastcolloc.R.drawable.bg_fc_profile_cover); error(com.iset.fastcolloc.R.drawable.ic_fastcoloc_symbol); diskCachePolicy(coil.request.CachePolicy.DISABLED); memoryCachePolicy(coil.request.CachePolicy.DISABLED) }
        }
        vm.cachedProfile.observe(viewLifecycleOwner) { cached ->
            if(vm.details.value == null && cached != null) binding.tvSummary.text = getString(com.iset.fastcolloc.R.string.s1_cached_summary, cached.firstName.orEmpty(), cached.lastName.orEmpty())
        }
        vm.details.observe(viewLifecycleOwner) { details ->
            if(details != null) {
                val p = details.profile
                binding.btnContinue.visibility = if(p.completedAt == null) View.VISIBLE else View.GONE
                binding.tvSummary.text = "${p.firstName.orEmpty()} ${p.lastName.orEmpty()}"
                binding.tvIdentityMeta.text = listOfNotNull(p.age?.let { "$it ans" }, "Membre FastColoc").joinToString(" · ")
                binding.tvProfileStatus.text = if (p.completedAt != null) "Profil complet · Prêt à trouver ta coloc" else "Inscription à compléter · ${p.validatedSteps.size}/5 étapes"
                binding.tvBiography.text = details.personal.string("biography").orEmpty().ifBlank { "Ta prochaine coloc commence par une présentation. Ajoute quelques mots sur toi et ton quotidien." }
                binding.tvLanguages.text = "Langues · " + details.personal.list("spoken_languages").ifBlank { "À renseigner" }
                binding.tvPhone.text = details.contacts.string("mobile_phone").orEmpty().ifBlank { "Non renseigné" }
                binding.tvAddress.text = listOfNotNull(details.address.string("street"), details.address.string("postal_code")).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Non renseignée" }
                val low = details.preferences.string("min_budget").orEmpty()
                val high = details.preferences.string("max_budget").orEmpty()
                binding.tvBudgetSummary.text = when {
                    low.isNotBlank() && high.isNotBlank() -> "$low – $high TND / mois"
                    high.isNotBlank() -> "Jusqu’à $high TND / mois"
                    low.isNotBlank() -> "À partir de $low TND / mois"
                    else -> "Ton budget, ta date et tes quartiers préférés."
                }
                binding.tvDetails.text = buildString {
                    appendLine("${p.firstName.orEmpty()} ${p.lastName.orEmpty()}")
                    appendLine(if(p.completedAt != null) "Profil complet" else "Inscription à compléter : ${p.validatedSteps.size}/5 étapes")
                    appendLine("${p.age ?: "—"} ans · ${p.gender.orEmpty()}")
                    appendLine("Adresse : ${details.address.string("street").orEmpty()} ${details.address.string("postal_code").orEmpty()}")
                    appendLine("Activités : ${details.personal.list("main_activities")}")
                    appendLine("Centres d’intérêt : ${details.personal.list("hobbies")}")
                    appendLine("Langues : ${details.personal.list("spoken_languages")}")
                    appendLine("Présentation : ${details.personal.string("biography").orEmpty()}")
                    appendLine("Mobile : ${details.contacts.string("mobile_phone").orEmpty()}")
                    appendLine("Disponibilités : ${details.contacts.string("call_availability").orEmpty()}")
                    append("${details.photos.size} photo(s) privée(s). Modifications partagées avec le Web.")
                }
                if(owner != p.id) {
                    owner = p.id
                    binding.etMinBudget.setText(details.preferences.string("min_budget")); binding.etMaxBudget.setText(details.preferences.string("max_budget")); binding.etMoveIn.setText(details.preferences.string("move_in_date"))
                    localities = details.preferredLocalities.toMutableSet()
                }
            }
        }
        binding.btnRefresh.setOnClickListener { owner = null; vm.refresh() }
        binding.btnContinue.setOnClickListener { vm.continueRegistration() }
        listOf(binding.btnEdit1, binding.btnEdit2, binding.btnEdit3, binding.btnEdit4, binding.btnEdit5).forEachIndexed { i, button -> button.setOnClickListener { vm.edit(i+1) } }
        binding.btnPlaces.setOnClickListener {
            val places = vm.localities.value.orEmpty()
            if(places.isEmpty()) vm.formError("Renseigne d’abord ton adresse ou actualise le profil. Les localités proposées sont celles du gouvernorat actuel.")
            else {
                val selected = localities.toMutableSet()
                AlertDialog.Builder(requireContext()).setTitle("Localités recherchées")
                    .setMultiChoiceItems(places.map { it.name }.toTypedArray(), places.map { it.id in selected }.toBooleanArray()) { _, index, checked -> if(checked) selected.add(places[index].id) else selected.remove(places[index].id) }
                    .setNegativeButton("Annuler", null).setPositiveButton("Choisir") { _, _ -> localities = selected }.show()
            }
        }
        binding.btnPreferences.setOnClickListener { form {
            fun number(value: String): Double? = if(value.isBlank()) null else value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw IllegalArgumentException("Budget invalide.")
            val date = binding.etMoveIn.text.toString().ifBlank { null }
            require(date == null || runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { isLenient = false }.parse(date) }.isSuccess && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Date attendue : AAAA-MM-JJ." }
            vm.preferences(PreferencesDto(number(binding.etMinBudget.text.toString()), number(binding.etMaxBudget.text.toString()), date, localities.toList()))
        } }
        binding.btnNotificationSettings.setOnClickListener { findNavController().navigate(com.iset.fastcolloc.R.id.notificationSettingsFragment) }
        binding.btnLogout.setOnClickListener { vm.logout() }
        vm.busy.observe(viewLifecycleOwner) { busy ->
            listOf(binding.btnRefresh,binding.btnContinue,binding.btnEdit1,binding.btnEdit2,binding.btnEdit3,binding.btnEdit4,binding.btnEdit5,binding.btnPreferences,binding.btnPlaces,binding.btnLogout).forEach { it.isEnabled = !busy }
        }
        if (vm.details.value == null || vm.photoUrls.value.isNullOrEmpty() && !vm.details.value?.photos.isNullOrEmpty()) vm.refresh()
    }
}
