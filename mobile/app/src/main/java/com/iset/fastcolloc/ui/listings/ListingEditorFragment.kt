package com.iset.fastcolloc.ui.listings

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.iset.fastcolloc.databinding.FragmentListingEditorBinding

class ListingEditorFragment :
    ListingBoundFragment<FragmentListingEditorBinding>(FragmentListingEditorBinding::inflate) {
    private var step = 1
    private var renderedId: String? = null
    private lateinit var photos: ListingPhotoAdapter
    private var version: String? = null
    private var locality: Long = 0
    private val picker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null && view != null)
                listings.upload(uri, binding.etPhotoCaption.text.toString())
        }

    private fun fields(): Map<String, EditText> =
        mapOf(
            "title" to binding.etListingTitle,
            "description" to binding.etListingDescription,
            "propertyType" to binding.etPropertyType,
            "roommateType" to binding.etRoommateType,
            "street" to binding.etStreet,
            "latitude" to binding.etLatitude,
            "longitude" to binding.etLongitude,
            "rent" to binding.etRent,
            "capacity" to binding.etCapacity,
            "equipment" to binding.etEquipment,
            "availableFrom" to binding.etAvailableFrom,
            "availableUntil" to binding.etAvailableUntil,
            "publicationDays" to binding.etPublicationDays,
        )

    override fun onViewCreated(view: View, state: Bundle?) {
        observeMessages()
        val id = arguments?.getString("listingId")?.ifBlank { null }
        listings.openEditor(id)
        binding.spKind.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Je propose une colocation", "Je recherche une colocation"),
            )
        photos = ListingPhotoAdapter { photo ->
            AlertDialog.Builder(requireContext())
                .setMessage("Supprimer cette photo ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer") { _, _ -> listings.deletePhoto(photo) }
                .show()
        }
        binding.rvPhotos.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPhotos.adapter = photos
        val draft = listings.draft
        if (draft.isNotEmpty()) {
            fields().forEach { (key, field) -> field.setText(draft[key].orEmpty()) }
            step = draft["step"]?.toIntOrNull() ?: 1
            binding.spKind.setSelection(if (draft["kind"] == "REQUEST") 1 else 0)
            locality = draft["localityId"]?.toLongOrNull() ?: 0
            version = draft["version"]
        } else {
            binding.etCapacity.setText("1")
            binding.etPublicationDays.setText("30")
        }
        fun places() {
            val rows = listings.areas.value.orEmpty()
            binding.spListingLocality.adapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("Choisir une localité") + rows.map { it.toString() },
                )
            binding.spListingLocality.setSelection(
                (rows.indexOfFirst { it.id == locality } + 1).coerceAtLeast(0)
            )
        }
        listings.areas.observe(viewLifecycleOwner) { places() }
        listings.selected.observe(viewLifecycleOwner) { row ->
            if (row != null) {
                if (row.authorId != listings.owner.value) {
                    binding.btnSaveListing.isEnabled = false
                    return@observe
                }
                if (renderedId != row.id) {
                    if (listings.draft.isEmpty()) {
                        val values =
                            mapOf(
                                "title" to row.title,
                                "description" to row.description.orEmpty(),
                                "propertyType" to row.propertyType,
                                "roommateType" to row.roommateType,
                                "street" to row.street.orEmpty(),
                                "latitude" to row.latitude.toString(),
                                "longitude" to row.longitude.toString(),
                                "rent" to row.rent.toString(),
                                "capacity" to row.capacity.toString(),
                                "equipment" to row.equipment.joinToString(", "),
                                "availableFrom" to row.availableFrom,
                                "availableUntil" to row.availableUntil.orEmpty(),
                                "publicationDays" to row.publicationDays.toString(),
                            )
                        fields().forEach { (key, field) -> field.setText(values[key]) }
                        binding.spKind.setSelection(if (row.kind == "REQUEST") 1 else 0)
                        locality = row.localityId ?: 0
                    }
                    version = version ?: row.version?.toString()
                    renderedId = row.id
                    places()
                }
                if (arguments == null) arguments = Bundle()
                requireArguments().putString("listingId", row.id)
                version = listings.draft["version"] ?: version
                photos.submitList(row.photos)
                render()
            }
        }
        listings.owner.observe(viewLifecycleOwner) { render() }
        listings.busy.observe(viewLifecycleOwner) { render() }
        listings.offline.observe(viewLifecycleOwner) { render() }
        binding.btnBackStep.setOnClickListener {
            step = (step - 1).coerceAtLeast(1)
            render()
        }
        binding.btnNextStep.setOnClickListener {
            snapshotDraft()
            step = (step + 1).coerceAtMost(3)
            render()
        }
        binding.btnSaveListing.setOnClickListener {
            snapshotDraft()
            listings.saveFields(
                listings.draft.toMap(),
                if (binding.spKind.selectedItemPosition == 1) "REQUEST" else "OFFER",
                locality,
            )
        }
        binding.btnAddListingPhoto.setOnClickListener {
            snapshotDraft()
            picker.launch("image/*")
        }
        binding.btnPublishListing.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(
                    "Publier ou renouveler la dernière version enregistrée ? Enregistre d’abord les modifications du formulaire."
                )
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Continuer") { _, _ -> listings.publish() }
                .show()
        }
        render()
    }

    private fun snapshotDraft() {
        locality =
            listings.areas.value
                .orEmpty()
                .getOrNull(binding.spListingLocality.selectedItemPosition - 1)
                ?.id ?: locality
        listings.draft.putAll(fields().mapValues { it.value.text.toString() })
        listings.draft["step"] = step.toString()
        listings.draft["kind"] =
            if (binding.spKind.selectedItemPosition == 1) "REQUEST" else "OFFER"
        listings.draft["localityId"] = locality.toString()
        version?.let { listings.draft["version"] = it }
    }

    private fun render() {
        val busy = listings.busy.value == true
        val offline = listings.offline.value == true
        val row = listings.selected.value
        val own = row == null || row.authorId == listings.owner.value
        binding.tvStep.text = "Étape $step / 3"
        binding.tvState.text =
            if (busy) "Chargement ou enregistrement…"
            else if (offline) "Copie locale · actualise la fiche avant de modifier."
            else "Enregistre ton annonce, ajoute les photos puis publie."
        binding.sectionPresentation.isVisible = step == 1
        binding.sectionLocation.isVisible = step == 2
        binding.sectionConditions.isVisible = step == 3
        binding.sectionPhotos.isVisible = row != null && own
        binding.btnBackStep.isEnabled = !busy && step > 1
        binding.btnNextStep.isVisible = step < 3
        binding.btnSaveListing.isVisible = step == 3
        binding.btnSaveListing.isEnabled = !busy && !offline && own
        binding.btnNextStep.isEnabled = !busy
        binding.btnPublishListing.isEnabled = !busy && !offline && own
        binding.btnAddListingPhoto.isEnabled =
            !busy && !offline && own && row?.photos.orEmpty().size < 10
        photos.enabled = !busy && !offline
    }

    override fun onDestroyView() {
        snapshotDraft()
        binding.rvPhotos.adapter = null
        super.onDestroyView()
    }
}
