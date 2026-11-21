package com.iset.fastcolloc.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class UiEvent<T>(private val value: T) {
    private var consumed = false
    fun consume(): T? = if (consumed) null else value.also { consumed = true }
}
class Sprint1ViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.get(application)
    private val repository = container.repository
    private val _details = MutableLiveData<DetailsDto?>()
    val details: LiveData<DetailsDto?> = _details
    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message
    private val _event = MutableLiveData<UiEvent<String>>()
    val event: LiveData<UiEvent<String>> = _event
    private val _countries = MutableLiveData<List<CountryDto>>(emptyList())
    val countries: LiveData<List<CountryDto>> = _countries
    private val _governorates = MutableLiveData<List<PlaceDto>>(emptyList())
    val governorates: LiveData<List<PlaceDto>> = _governorates
    private val _localities = MutableLiveData<List<PlaceDto>>(emptyList())
    val localities: LiveData<List<PlaceDto>> = _localities
    private val _photoUrls = MutableLiveData<Map<String, String>>(emptyMap())
    val photoUrls: LiveData<Map<String, String>> = _photoUrls
    val cachedProfile = repository.cachedProfile
    var recovery = false
        private set
    var editing = false
    var pendingEmail = ""
    private var pendingIdentity: IdentityDto? = null // Memory only, no password.
    private var geoGeneration = 0
    private var placeGeneration = 0
    private fun emit(value: String) { _event.value = UiEvent(value) }
    private fun action(block: suspend () -> Unit) {
        if (_busy.value == true) return
        _busy.value = true; _message.value = null
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _message.value = AuthRepository.message(e)
                if (e is SessionExpired || e is AccountBlocked) { _details.value = null; _photoUrls.value = emptyMap(); emit("login") }
            } finally { _busy.value = false }
        }
    }
    fun restore() = action {
        container.prepareLegacy()
        if (container.sessions.hasValidSession()) { _details.value = repository.restore(); emit(destination()) }
    }
    private fun destination(): String {
        val p = _details.value?.profile ?: return "login"
        return if (p.completedAt != null) "home" else "step" + ((1..5).firstOrNull { it !in p.validatedSteps } ?: 5)
    }
    fun login(email: String, password: String) = action {
        require(email.isNotBlank() && password.isNotBlank()) { "Saisis ton e-mail et ton mot de passe." }
        _details.value = repository.login(email, password)
        if (email.trim().equals(pendingEmail, true) && 1 !in _details.value!!.profile.validatedSteps) {
            pendingIdentity?.let { _details.value = repository.authorized { identity(it) } }
        }
        pendingIdentity = null; pendingEmail = ""; editing = false; emit(destination())
    }
    fun signup(email: String, password: String, confirm: String, identity: IdentityDto) = action {
        validateIdentity(identity)
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { "Adresse e-mail invalide." }
        require(password == confirm) { "Les mots de passe ne correspondent pas." }
        repository.signup(email, password)
        pendingEmail = email.trim(); pendingIdentity = identity; emit("verify")
    }
    fun google() = action { emit("browser:" + container.oauth.begin()) }
    fun callback(uri: Uri) = action {
        recovery = container.oauth.accept(uri)
        if (recovery) emit("reset") else {
            _details.value = repository.details()
            val verifiedIdentity = repository.authorized { session() }
            if(verifiedIdentity.email.equals(pendingEmail, true) && 1 !in _details.value!!.profile.validatedSteps) {
                pendingIdentity?.let { _details.value = repository.authorized { identity(it) } }
            }
            pendingIdentity = null; pendingEmail = ""; editing = false; emit(destination())
        }
    }
    fun recover(email: String) = action {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { "Adresse e-mail invalide." }
        repository.recover(email); _message.value = "Si la demande est admissible, un e-mail sera envoyé."
    }
    fun resend(email: String) = action {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { "Adresse e-mail invalide." }
        repository.resend(email); _message.value = "Si la demande est admissible, le lien sera envoyé."
    }
    fun reset(password: String, confirm: String) = action {
        require(recovery) { "Ouvre un lien de récupération valide." }
        AuthRepository.validatePassword(password); require(password == confirm) { "Les mots de passe ne correspondent pas." }
        repository.authorized { password(PasswordRequest(password)) }; logoutInternal()
    }
    private suspend fun logoutInternal() {
        val revoked = repository.logout()
        _details.value = null; _photoUrls.value = emptyMap(); pendingIdentity = null; pendingEmail = ""; recovery = false
        editing = false; emit("login")
        if (!revoked) _message.value = "Session locale fermée ; révocation distante non confirmée."
    }
    fun logout() = action { logoutInternal() }
    fun refresh() = action { _details.value = repository.details(); refreshPhotos(); loadGeography() }
    fun edit(step: Int) { editing = true; emit("step$step") }
    fun continueRegistration() { editing = false; emit(destination()) }
    fun saveIdentity(value: IdentityDto) = action {
        validateIdentity(value); _details.value = repository.authorized { identity(value) }; next(1)
    }
    private fun validateIdentity(value: IdentityDto) {
        require(value.firstName.isNotBlank() && value.lastName.isNotBlank() && value.firstName.length <= 100 && value.lastName.length <= 100 && value.age in 0..130) { "Nom, prénom et âge entre 0 et 130 requis." }
    }
    fun saveAddress(value: GeographyDto) = action {
        require(value.street.isNotBlank() && value.postalCode.isNotBlank() && value.localityId > 0 && value.governorateId > 0) { "Complète l’adresse et sélectionne une localité du référentiel." }
        _details.value = repository.authorized { geography(value) }; next(2)
    }
    fun savePersonal(value: PersonalDto) = action { _details.value = repository.authorized { personal(value) }; next(3) }
    fun saveContacts(value: ContactsDto) = action { _details.value = repository.authorized { contacts(value) }; next(4) }
    private fun next(step: Int) { emit(if (editing) "profile" else "step${step + 1}") }
    fun loadCountries() {
        if (_countries.value.orEmpty().isNotEmpty()) return
        action { _countries.value = repository.authorized { countries() } }
    }
    fun geography() = action { loadGeography() }
    private suspend fun loadGeography() {
        _countries.value = repository.authorized { countries() }
        val country = _details.value?.address?.get("country_code")?.takeUnless { it.isJsonNull }?.asString ?: "TN"
        _governorates.value = repository.authorized { governorates(country) }
        val id = _details.value?.address?.get("governorate_id")?.takeUnless { it.isJsonNull }?.asLong
        _localities.value = if (id != null) repository.authorized { localities(id) } else emptyList()
    }
    fun country(code: String) {
        val version = ++geoGeneration; ++placeGeneration
        _governorates.value = emptyList(); _localities.value = emptyList()
        viewModelScope.launch { try {
            val result = repository.authorized { governorates(code) }
            if (version == geoGeneration) _governorates.value = result
        } catch (e: Exception) { if (e is CancellationException) throw e; _message.value = AuthRepository.message(e) } }
    }
    fun governorate(id: Long) {
        val version = ++placeGeneration; _localities.value = emptyList()
        viewModelScope.launch { try {
            val result = repository.authorized { localities(id) }
            if (version == placeGeneration) _localities.value = result
        } catch (e: Exception) { if (e is CancellationException) throw e; _message.value = AuthRepository.message(e) } }
    }
    fun photos() = action { _details.value = repository.details(); refreshPhotos() }
    private suspend fun refreshPhotos() {
        _photoUrls.value = _details.value?.photos.orEmpty().associate { it.id to repository.authorized { signed(it.id) }.url }
    }
    fun upload(uri: Uri, caption: String) = action {
        require(_details.value?.photos.orEmpty().size < 5) { "Cinq photos maximum." }
        val resolver = getApplication<Application>().contentResolver
        val mime = resolver.getType(uri) ?: ""
        require(mime in listOf("image/jpeg", "image/png", "image/webp")) { "Formats autorisés : JPEG, PNG, WebP." }
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    require(out.size() + count <= 5242880) { "Photo limitée à 5 MiB." }; out.write(buffer, 0, count)
                }; out.toByteArray()
            } ?: throw IllegalArgumentException("Photo illisible.")
        }
        require(bytes.isNotEmpty()) { "Photo vide." }
        val part = MultipartBody.Part.createFormData("file", "profile." + if(mime == "image/png") "png" else if(mime == "image/webp") "webp" else "jpg", bytes.toRequestBody(mime.toMediaType()))
        repository.authorized { upload(part, caption.toRequestBody("text/plain".toMediaType())) }
        _details.value = repository.details(); refreshPhotos(); _message.value = "Photo enregistrée."
    }
    fun deletePhoto(id: String) = action {
        repository.authorized { deletePhoto(id) }; _details.value = repository.details(); refreshPhotos()
        _message.value = "Photo supprimée. Revalide l’étape 5."
    }
    fun caption(id: String, value: String) = action { repository.authorized { caption(id, CaptionRequest(value)) }; _details.value = repository.details(); _message.value = "Description enregistrée." }
    fun complete() = action {
        val ids = _details.value?.photos.orEmpty().map { it.id }
        require(ids.isNotEmpty()) { "Ajoute au moins une photo." }
        repository.authorized { photos(PhotoIds(ids)) }; _details.value = repository.authorized { complete() }
        emit("profile")
    }
    fun preferences(value: PreferencesDto) = action {
        require(value.minBudget == null || value.minBudget >= 0) { "Budget négatif." }
        require(value.maxBudget == null || value.maxBudget >= 0) { "Budget négatif." }
        require(value.minBudget == null || value.maxBudget == null || value.minBudget <= value.maxBudget) { "Budget minimum supérieur au maximum." }
        _details.value = repository.authorized { preferences(value) }; _message.value = "Préférences enregistrées."
    }
    fun formError(value: String) { _message.value = value }
}
