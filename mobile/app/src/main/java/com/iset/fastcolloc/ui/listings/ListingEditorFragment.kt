package com.iset.fastcolloc.ui.listings

import android.os.Bundle
import com.iset.fastcolloc.ui.auth.calendarField
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
    private var step = 0
    private var regionId: Long? = null
    private val isRequest get() = binding.spKind.selectedItemPosition == 1
    private val steps get() = if (isRequest) listOf("Ton envie", "Ma recherche", "Zone et budget", "Préférences", "Vérification et publication") else listOf("Ton envie", "Mon logement", "Loyer et disponibilité", "Équipements et photos", "Vérification et publication")
    private fun towns() = listings.areas.value.orEmpty().filter { it.governorateId == regionId }
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
        calendarField(binding.etAvailableFrom, "Disponible à partir du", maximum = { binding.etAvailableUntil.text.toString().ifBlank { null } })
        calendarField(binding.etAvailableUntil, "Disponible jusqu’au", optional = true, minimum = { binding.etAvailableFrom.text.toString().ifBlank { null } })
        val id = arguments?.getString("listingId")?.ifBlank { null }
        listings.openEditor(id)
        binding.spKind.adapter =
            ArrayAdapter(
                requireContext(),
                com.iset.fastcolloc.R.layout.item_fc_dropdown,
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
            step = draft["flowStep"]?.toIntOrNull() ?: if (id == null) 0 else 1
            binding.spKind.setSelection(if (draft["kind"] == "REQUEST") 1 else 0)
            locality = draft["localityId"]?.toLongOrNull() ?: 0
            version = draft["version"]
            binding.etPhotoCaption.setText(draft["photoCaption"].orEmpty())
        } else {
            binding.etCapacity.setText("1")
            binding.etPublicationDays.setText("30")
        }
        fun places() {
            val rows = listings.areas.value.orEmpty()
            if (regionId == null && locality > 0) regionId = rows.find { it.id == locality }?.governorateId
            val regions = rows.distinctBy { it.governorateId }
            fun fillTowns() {
                val towns = towns()
                binding.spListingLocality.adapter = ArrayAdapter(requireContext(), com.iset.fastcolloc.R.layout.item_fc_dropdown, listOf("Choisir une localité") + towns.map { it.locality })
                binding.spListingLocality.setSelection((towns.indexOfFirst { it.id == locality } + 1).coerceAtLeast(0))
            }
            binding.spListingRegion.onItemSelectedListener = null
            binding.spListingRegion.adapter = ArrayAdapter(requireContext(), com.iset.fastcolloc.R.layout.item_fc_dropdown, listOf("Choisir un gouvernorat") + regions.map { it.governorate })
            binding.spListingRegion.setSelection((regions.indexOfFirst { it.governorateId == regionId } + 1).coerceAtLeast(0))
            binding.spListingRegion.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = regions.getOrNull(position - 1)?.governorateId
                    if (next != regionId) { regionId = next; locality = 0; fillTowns() }
                }
            }
            fillTowns()
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
                                "latitude" to row.latitude?.toString().orEmpty(),
                                "longitude" to row.longitude?.toString().orEmpty(),
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
        binding.btnOffer.setOnClickListener { binding.spKind.setSelection(0); render() }
        binding.btnRequest.setOnClickListener { binding.spKind.setSelection(1); render() }
        binding.btnBackStep.setOnClickListener { snapshotDraft(); step = (step - 1).coerceAtLeast(0); render() }
        binding.btnNextStep.setOnClickListener {
            snapshotDraft()
            if (validateCurrent()) { step = (step + 1).coerceAtMost(4); render() }
        }
        fun save(publish: Boolean = false) {
            snapshotDraft()
            listings.saveFields(listings.draft.toMap(), if (isRequest) "REQUEST" else "OFFER", locality, publish)
        }
        binding.btnSaveListing.setOnClickListener { save() }
        binding.btnSaveForPhotos.setOnClickListener { save() }
        binding.btnAddListingPhoto.setOnClickListener { snapshotDraft(); picker.launch("image/*") }
        binding.btnPublishListing.setOnClickListener { save(true) }
        render()
    }

    private fun snapshotDraft() {
        locality =
            towns()
                .getOrNull(binding.spListingLocality.selectedItemPosition - 1)
                ?.id ?: locality
        listings.draft.putAll(fields().mapValues { it.value.text.toString() })
        listings.draft["photoCaption"] = binding.etPhotoCaption.text.toString()
        listings.draft["flowStep"] = step.toString()
        listings.draft["kind"] =
            if (binding.spKind.selectedItemPosition == 1) "REQUEST" else "OFFER"
        listings.draft["localityId"] = locality.toString()
        version?.let { listings.draft["version"] = it }
    }

    private fun validateCurrent(): Boolean {
        val f = fields().mapValues { it.value.text.toString().trim() }
        fun fail(message: String): Boolean { listings.formError(message); return false }
        if (step == 1 && (f["title"].orEmpty().length !in 5..160 || f["description"].orEmpty().length < 20 || f["propertyType"].isNullOrBlank() || (f["capacity"]?.toIntOrNull() ?: 0) !in 1..50)) return fail("Vérifie le titre (5 caractères minimum), la présentation (20 caractères minimum), le type et le nombre de personnes.")
        if ((step == 1 && !isRequest || step == 2 && isRequest) && locality <= 0) return fail("Choisis un gouvernorat et une localité.")
        if (step == 1 && !isRequest && (f["street"].isNullOrBlank() || (f["latitude"]?.toDoubleOrNull() ?: 0.0) !in 30.0..38.0 || (f["longitude"]?.toDoubleOrNull() ?: 0.0) !in 7.0..12.0)) return fail("Indique l’adresse et les coordonnées du logement en Tunisie.")
        if (step == 2 && (f["rent"]?.replace(',', '.')?.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..999999999.999 } != true)) return fail("Indique un montant positif en TND.")
        if ((step == 2 && !isRequest || step == 3 && isRequest) && (f["availableFrom"].isNullOrBlank() || !f["availableUntil"].isNullOrBlank() && f["availableUntil"].orEmpty() < f["availableFrom"].orEmpty())) return fail("Choisis une date de début et vérifie la date de fin.")
        return true
    }
    private fun render() {
        val busy = listings.busy.value == true
        val offline = listings.offline.value == true
        val row = listings.selected.value
        val own = row == null || row.authorId == listings.owner.value
        fields().values.forEach { it.isEnabled = !busy }
        binding.spListingRegion.isEnabled = !busy
        binding.spListingLocality.isEnabled = !busy
        binding.etPhotoCaption.isEnabled = !busy
        binding.tvStep.text = "${step + 1} / 5 · ${steps[step]}"
        binding.stepProgress.setProgressCompat(step, com.iset.fastcolloc.ui.motion.Motion.enabled(requireContext()))
        binding.tvState.text = if (busy) "Enregistrement en cours…" else if (offline) "Copie locale · actualise avant de modifier." else if (isRequest) "Une recherche à ton image, sans photos ni GPS obligatoires." else "Présente ton logement, à ton rythme."
        binding.sectionKind.isVisible = step == 0
        binding.tvKindSelection.text = if (isRequest) "Sélection : je cherche une coloc" else "Sélection : je propose un logement"
        binding.btnOffer.isChecked = !isRequest
        binding.btnRequest.isChecked = isRequest
        binding.btnOffer.isEnabled = !busy
        binding.btnRequest.isEnabled = !busy
        binding.btnOffer.strokeWidth = if (isRequest) 2 else 6
        binding.btnRequest.strokeWidth = if (isRequest) 6 else 2
        binding.sectionPresentation.isVisible = step == 1
        binding.sectionLocation.isVisible = if (isRequest) step == 2 else step == 1
        binding.fieldLatitude.isVisible = !isRequest
        binding.fieldLongitude.isVisible = !isRequest
        binding.sectionRent.isVisible = step == 2
        binding.sectionConditions.isVisible = if (isRequest) step == 3 else step == 2
        binding.sectionPreferences.isVisible = step == 3
        binding.sectionPublication.isVisible = step == 4 || !isRequest && step == 2
        binding.sectionPhotos.isVisible = !isRequest && step == 3
        binding.sectionReview.isVisible = step == 4
        binding.labelListingDescription.text = if (isRequest) "Présente ta recherche" else "Décris ton logement"
        binding.labelPropertyType.text = if (isRequest) "Type de logement recherché" else "Type de logement"
        binding.labelCapacity.text = if (isRequest) "Nombre de personnes concernées" else "Capacité du logement (personnes)"
        binding.labelStreet.text = if (isRequest) "Secteur souhaité (facultatif)" else "Adresse du logement"
        binding.labelRent.text = if (isRequest) "Budget maximum par mois (TND)" else "Loyer mensuel (TND)"
        binding.labelEquipment.text = if (isRequest) "Équipements souhaités" else "Équipements disponibles"
        binding.labelAvailableFrom.text = if (isRequest) "Date d’emménagement souhaitée" else "Disponible à partir du"
        binding.labelAvailableUntil.text = if (isRequest) "Fin de recherche (facultatif)" else "Disponible jusqu’au (facultatif)"
        binding.btnBackStep.isEnabled = !busy && step > 0
        binding.btnNextStep.isVisible = step < 4
        binding.btnNextStep.isEnabled = !busy
        binding.btnSaveListing.isVisible = step == 4
        binding.btnPublishListing.isVisible = step == 4
        binding.btnSaveListing.isEnabled = !busy && !offline && own
        binding.btnPublishListing.isEnabled = !busy && !offline && own
        binding.btnSaveListing.text = if (row?.status == "PUBLISHED") "Enregistrer les modifications" else "Garder en brouillon"
        binding.btnPublishListing.text = if (row?.status == "PUBLISHED") "Enregistrer et renouveler" else "Publier mon annonce"
        binding.btnSaveForPhotos.isVisible = row == null
        binding.btnSaveForPhotos.isEnabled = !busy && !offline
        binding.btnAddListingPhoto.isVisible = row != null
        binding.btnAddListingPhoto.isEnabled = !busy && !offline && own && row?.photos.orEmpty().size < 10
        binding.etPhotoCaption.isVisible = row != null
        binding.tvPhotoHint.isVisible = row == null
        photos.enabled = !busy && !offline
        if (step == 4) {
            snapshotDraft()
            val f = listings.draft
            val place = listings.areas.value.orEmpty().find { it.id == locality }
            binding.tvReview.text = buildString {
                appendLine(if (isRequest) "JE CHERCHE UNE COLOC" else "JE PROPOSE UN LOGEMENT")
                appendLine(f["title"]); appendLine(); appendLine(f["description"]); appendLine()
                appendLine("Zone : ${place ?: "À préciser"}")
                if (!f["street"].isNullOrBlank()) appendLine("Secteur : ${f["street"]}")
                appendLine("${if(isRequest) "Budget maximum" else "Loyer"} : ${f["rent"]} TND / mois")
                appendLine("${f["propertyType"]} · ${f["capacity"]} personne(s)")
                appendLine("Date : ${f["availableFrom"]}" + if(f["availableUntil"].isNullOrBlank()) "" else " → ${f["availableUntil"]}")
                appendLine("Équipements : ${f["equipment"].orEmpty().ifBlank { "Non renseignés" }}")
                appendLine("Colocataire : ${f["roommateType"].orEmpty().ifBlank { "Aucune préférence" }}")
                if (!isRequest) appendLine("${row?.photos.orEmpty().size} photo(s) du logement")
            }
        }
    }

    override fun onDestroyView() {
        snapshotDraft()
        binding.rvPhotos.adapter = null
        super.onDestroyView()
    }
}
