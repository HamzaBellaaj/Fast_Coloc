package com.iset.fastcolloc.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.*
import kotlinx.coroutines.*

data class RecommendationState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val items: List<RecommendationDto> = emptyList(),
    val incomplete: Boolean = false,
    val error: String? = null,
    val pending: Set<String> = emptySet(),
)

class RecommendationViewModel(private val repository: ListingRepository) : ViewModel() {
    private val _state = MutableLiveData(RecommendationState())
    val state: LiveData<RecommendationState> = _state
    private val _event = MutableLiveData<UiEvent<String>>()
    val event: LiveData<UiEvent<String>> = _event
    private fun update(change: (RecommendationState) -> RecommendationState) {
        _state.value = change(_state.value!!)
    }
    fun open() { if (_state.value?.loaded != true) refresh() }
    fun refresh() {
        if (_state.value!!.loading || _state.value!!.pending.isNotEmpty()) return
        update { it.copy(loading = true, loaded = true, items = emptyList(), error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.recommendations() }
                update { it.copy(items = result.recommendations, incomplete = result.profileIncomplete) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { failure(e)
            } finally { update { it.copy(loading = false) } }
        }
    }
    fun favorite(row: ListingDto) {
        if (_state.value!!.loading || row.id in _state.value!!.pending) return
        update { it.copy(pending = it.pending + row.id, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.favorite(row) }
                syncFavorite(row.copy(favorite = !row.favorite))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { failure(e)
            } finally { update { it.copy(pending = it.pending - row.id) } }
        }
    }
    fun syncFavorite(row: ListingDto) {
        update { it.copy(items = it.items.map { item ->
            if (item.listing.id == row.id) item.copy(listing = item.listing.copy(favorite = row.favorite)) else item
        }) }
    }
    private fun failure(error: Exception) {
        val message = if (error is retrofit2.HttpException) runCatching {
            com.google.gson.JsonParser.parseString(error.response()?.errorBody()?.string()).asJsonObject.get("message").asString
        }.getOrNull() ?: AuthRepository.message(error) else AuthRepository.message(error)
        update { it.copy(error = message) }
        if (error is SessionExpired || error is AccountBlocked) _event.value = UiEvent("login")
    }
}

class RecommendationViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RecommendationViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return RecommendationViewModel(AppContainer.get(application).listings) as T
    }
}
