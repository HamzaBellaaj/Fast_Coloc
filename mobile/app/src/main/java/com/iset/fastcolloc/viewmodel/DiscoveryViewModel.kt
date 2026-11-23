package com.iset.fastcolloc.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.*
import kotlinx.coroutines.*

data class DiscoveryState(val items: List<ListingDto> = emptyList(), val loading: Boolean = false,
    val acting: Boolean = false, val exhausted: Boolean = false, val error: String? = null)

/** One queue per signed-in activity; processed ids survive fragment recreation/navigation. */
class DiscoveryViewModel(private val repository: ListingRepository) : ViewModel() {
    private val _state = MutableLiveData(DiscoveryState())
    val state: LiveData<DiscoveryState> = _state
    private val processed = linkedSetOf<String>()
    private var owner: String? = null
    private var nextPage = 0
    private var ended = false
    fun open() { load() }
    fun refresh() { if (!_state.value!!.loading && !_state.value!!.acting) { nextPage = 0; ended = false; load() } }
    fun load() {
        if (_state.value!!.loading || _state.value!!.acting) return
        _state.value = _state.value!!.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val account = withContext(Dispatchers.IO) { repository.owner() }
                if (account != owner) { owner = account; processed.clear(); nextPage = 0; ended = false; _state.value = DiscoveryState(loading = true) }
                if (ended) { _state.value = _state.value!!.copy(exhausted = _state.value!!.items.isEmpty()); return@launch }
                var queue = _state.value!!.items
                while (queue.isEmpty() && !ended) {
                    val page = withContext(Dispatchers.IO) { repository.catalog(mapOf("size" to "20", "page" to nextPage.toString())) }
                    nextPage++
                    ended = nextPage * page.size >= page.total || page.items.isEmpty()
                    queue = page.items.filter { it.id !in processed && it.authorId != account && available(it) }.distinctBy { it.id }
                }
                _state.value = _state.value!!.copy(items = queue, exhausted = ended && queue.isEmpty())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value!!.copy(error = AuthRepository.message(e)) }
            finally { _state.value = _state.value!!.copy(loading = false) }
        }
    }
    fun decide(interested: Boolean) {
        val state = _state.value!!
        val row = state.items.firstOrNull() ?: return
        if (state.loading || state.acting) return
        _state.value = state.copy(acting = true, error = null)
        viewModelScope.launch {
            try {
                // A stale card is re-read before acting; hidden/deleted/expired offers cannot be liked.
                if (interested) withContext(Dispatchers.IO) {
                    val current = repository.detail(row.id)
                    require(!current.second && available(current.first)) { "Cette annonce n’est plus disponible." }
                    repository.interested(row.id)
                }
                advance(row.id)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 404 || e is IllegalArgumentException) advance(row.id)
                _state.value = _state.value!!.copy(error = AuthRepository.message(e))
            } finally {
                _state.value = _state.value!!.copy(acting = false)
                if (_state.value!!.items.isEmpty() && _state.value!!.error == null) load()
            }
        }
    }
    private fun advance(id: String) {
        processed.add(id)
        _state.value = _state.value!!.copy(items = _state.value!!.items.filterNot { it.id == id }, exhausted = ended && _state.value!!.items.size <= 1)
    }
    companion object {
        fun available(row: ListingDto): Boolean = row.status == "PUBLISHED" && !row.isHidden &&
            row.expiresAt?.let { runCatching { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC"); isLenient = false }.parse(it.take(19))!!.time > System.currentTimeMillis() }.getOrDefault(false) } == true
    }
}

class DiscoveryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DiscoveryViewModel::class.java))
        @Suppress("UNCHECKED_CAST") return DiscoveryViewModel(AppContainer.get(application).listings) as T
    }
}
