package com.iset.fastcolloc.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.*
import kotlinx.coroutines.*

data class MapState(val items: List<ListingMarkerDto> = emptyList(), val loading: Boolean = false,
    val loaded: Boolean = false, val error: String? = null, val center: GeoCoordinatesDto? = null)
class MapViewModel(private val repository: ListingRepository) : ViewModel() {
    private val _state = MutableLiveData(MapState())
    val state: LiveData<MapState> = _state
    private var job: Job? = null
    var filters: Map<String,String> = emptyMap()
        private set
    fun load(query: Map<String,String> = filters) {
        job?.cancel()
        filters = query.filterKeys { it !in setOf("page", "size") }
        _state.value = MapState(loading = true)
        val requested = filters.toMap()
        job = viewModelScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    val items = linkedMapOf<String,ListingMarkerDto>()
                    var page = 0
                    do {
                        val result = repository.map(requested + mapOf("page" to page.toString(), "size" to "50"))
                        for (row in result.items) if (valid(row)) items[row.id] = row
                        page++
                        if (result.items.isEmpty() || page * result.size >= result.total) break
                        require(page <= 1000) { "Précise les filtres pour réduire la zone de recherche." }
                    } while (true)
                    items.values.toList()
                }
                // An empty area still has a geographic center; finding it must not fail the listing query.
                val center = if (rows.isEmpty()) withContext(Dispatchers.IO) {
                    runCatching {
                        val locality = requested["localityId"]?.toLongOrNull()
                        val region = requested["governorateId"]?.toLongOrNull()
                        val area = if (locality != null || region != null) repository.areas().firstOrNull {
                            if (locality != null) it.id == locality else it.governorateId == region
                        } else null
                        val text = if (locality != null && area != null) area.toString() else area?.governorate ?: requested["q"].orEmpty()
                        if (text.length < 3) null else repository.searchAddress(text).firstOrNull()?.let { GeoCoordinatesDto(it.latitude,it.longitude) }
                    }.getOrElse { e -> if (e is CancellationException) throw e; null }
                } else null
                _state.value = MapState(items = rows, loaded = true, center = center)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = MapState(loaded = true, error = AuthRepository.message(e)) }
        }
    }
    companion object {
        fun valid(row: ListingMarkerDto) = row.kind == "OFFER" && row.status == "PUBLISHED" && !row.isHidden &&
            row.latitude?.let { it.isFinite() && it in 30.0..38.0 } == true &&
            row.longitude?.let { it.isFinite() && it in 7.0..12.0 } == true
    }
}
class MapViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MapViewModel::class.java))
        @Suppress("UNCHECKED_CAST") return MapViewModel(AppContainer.get(application).listings) as T
    }
}
