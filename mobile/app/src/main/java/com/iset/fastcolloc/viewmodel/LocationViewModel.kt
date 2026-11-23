package com.iset.fastcolloc.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.*
import kotlinx.coroutines.*

data class LocationState(val position: GeoPositionDto? = null, val results: List<GeoPlaceDto> = emptyList(),
    val loading: Boolean = false, val message: String = "Choisis un point ou recherche une adresse.",
    val revision: Int = 0, val restore: Boolean = false)
class LocationViewModel(private val repository: ListingRepository) : ViewModel() {
    private val _state = MutableLiveData(LocationState())
    val state: LiveData<LocationState> = _state
    private var job: Job? = null
    private var generation = 0
    fun initial(latitude: Double?, longitude: Double?, address: String) {
        if (_state.value!!.position == null && latitude != null && longitude != null)
            _state.value = _state.value!!.copy(position = GeoPositionDto(latitude, longitude, address, true))
    }
    fun search(query: String) {
        if (query.trim().length !in 3..250) { error("Saisis une adresse de 3 à 250 caractères."); return }
        execute {
            val places = withContext(Dispatchers.IO) { repository.searchAddress(query.trim()) }
            _state.value = _state.value!!.copy(results = places,
                message = if (places.isEmpty()) "Aucune adresse trouvée. Précise la ville et la rue." else "Choisis explicitement une adresse ci-dessous.")
        }
    }
    fun validate(latitude: Double?, longitude: Double?) {
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite()) {
            job?.cancel(); generation++
            _state.value = _state.value!!.copy(loading = false)
            error("Renseigne les deux coordonnées numériques."); return
        }
        execute {
            val point = withContext(Dispatchers.IO) { repository.validatePosition(GeoCoordinatesDto(latitude, longitude)) }
            _state.value = _state.value!!.copy(position = point, results = emptyList(), revision = _state.value!!.revision + 1,
                message = if (point.address.isBlank()) "Position terrestre validée. Précise l’adresse manuellement."
                else if (point.approximate) "Adresse approximative : vérifie et précise le numéro ou la rue." else "Position terrestre validée. Vérifie l’adresse.")
        }
    }
    private fun execute(action: suspend () -> Unit) {
        job?.cancel()
        val current = ++generation
        _state.value = _state.value!!.copy(loading = true, restore = false)
        job = viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(message(e)) }
            finally { if (current == generation) _state.value = _state.value!!.copy(loading = false) }
        }
    }
    private fun error(text: String) { _state.value = _state.value!!.copy(message = text, restore = true, revision = _state.value!!.revision + 1) }
    private fun message(e: Exception): String = if (e is retrofit2.HttpException) runCatching {
        com.google.gson.JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject.get("message").asString
    }.getOrNull() ?: AuthRepository.message(e) else AuthRepository.message(e)
}
class LocationViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LocationViewModel::class.java))
        @Suppress("UNCHECKED_CAST") return LocationViewModel(AppContainer.get(application).listings) as T
    }
}
