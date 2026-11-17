package com.iset.fastcolloc.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.*
import java.io.IOException
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private data class ListingScope(val owner: String, val key: String)

/** Chap.5: shared state; TP6: Room LiveData; TP7: IO coroutines + postValue. */
class ListingViewModel(
    private val repository: ListingRepository,
    private val application: Application,
) : ViewModel() {
    private val scope = MutableLiveData<ListingScope?>()
    val snapshot =
        scope.switchMap { s ->
            if (s == null) MutableLiveData(null) else repository.observe(s.owner, s.key)
        }
    val page = snapshot.map { it?.let(repository::decode) }
    private val _selected = MutableLiveData<ListingDto?>()
    val selected: LiveData<ListingDto?> = _selected
    private val _areas = MutableLiveData<List<ListingAreaDto>>(emptyList())
    val areas: LiveData<List<ListingAreaDto>> = _areas
    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy
    private val _offline = MutableLiveData(false)
    val offline: LiveData<Boolean> = _offline
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message
    private val _event = MutableLiveData<UiEvent<String>>()
    val event: LiveData<UiEvent<String>> = _event
    private val _owner = MutableLiveData<String>()
    val owner: LiveData<String> = _owner
    var mode = "catalog"
        private set

    var query: Map<String, String> = emptyMap()
        private set

    var pageNumber = 0
        private set

    var editorId: String? = null
        private set

    var draft = mutableMapOf<String, String>()
    private var loadJob: Job? = null
    private var workJob: Job? = null

    fun load(
        mode: String = this.mode,
        query: Map<String, String> = this.query,
        page: Int = this.pageNumber,
    ) {
        this.mode = mode
        this.query = query
        pageNumber = page
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                _busy.postValue(true)
                _message.postValue(null)
                try {
                    val owner = repository.owner()
                    _owner.postValue(owner)
                    val params = query + mapOf("page" to page.toString(), "size" to "12")
                    scope.postValue(ListingScope(owner, repository.key(mode, params)))
                    repository.refresh(mode, params)
                    _offline.postValue(false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _offline.postValue(
                        e is IOException && e !is SessionExpired && e !is AccountBlocked
                    )
                    failure(e)
                } finally {
                    _busy.postValue(false)
                }
            }
    }

    fun loadAreas() {
        if (_areas.value?.isNotEmpty() == true) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _areas.postValue(repository.areas())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure(e)
            }
        }
    }

    fun detail(id: String) {
        loadJob?.cancel()
        _selected.value = null
        work {
            val pair = repository.detail(id)
            _selected.postValue(pair.first)
            _offline.postValue(pair.second)
            if (pair.second)
                _message.postValue("Copie locale : vérifie les informations en actualisant.")
        }
    }

    fun openEditor(id: String?) {
        if (editorId != id) {
            draft.clear()
            _selected.value = null
        }
        editorId = id
        if (id != null) detail(id)
        loadAreas()
    }

    fun beginEditor(id: String?) {
        draft.clear()
        editorId = id
        _selected.value = null
    }

    fun saveFields(fields: Map<String, String>, kind: String, locality: Long, publishAfter: Boolean = false) {
        try {
            save(
                ListingDraft.parse(
                    fields,
                    kind,
                    locality,
                    fields["version"]?.toLongOrNull() ?: _selected.value?.version,
                ), publishAfter
            )
        } catch (e: IllegalArgumentException) {
            formError(e.message ?: "Vérifie les champs.")
        }
    }

    fun save(body: ListingWriteDto, publishAfter: Boolean = false) {
        work {
            val row = repository.save(editorId, body)
            editorId = row.id
            draft["version"] = row.version.toString()
            _selected.postValue(row)
            _offline.postValue(false)
            _message.postValue("Annonce enregistrée.")
            if (publishAfter) {
                val published = repository.publish(row)
                _selected.postValue(published)
                draft["version"] = published.version.toString()
                _event.postValue(UiEvent("detail:" + published.id))
            }
        }
    }

    fun publish() {
        val row = _selected.value ?: return
        work {
            val published = repository.publish(row)
            _selected.postValue(published)
            _event.postValue(UiEvent("detail:" + published.id))
        }
    }

    fun delete() {
        val row = _selected.value ?: return
        work {
            repository.delete(row.id)
            _selected.postValue(null)
            draft.clear()
            _event.postValue(UiEvent("list"))
        }
    }

    fun favorite(row: ListingDto) {
        work {
            repository.favorite(row)
            val updated = repository.detail(row.id)
            _selected.postValue(updated.first)
            _offline.postValue(false)
            load()
        }
    }

    fun upload(uri: Uri, caption: String) {
        val row = _selected.value ?: return
        work {
            val bytes =
                application.contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        require(out.size() + count <= 5242880) { "Photo supérieure à 5 MiB." }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                } ?: throw IllegalArgumentException("Image illisible.")
            require(bytes.size in 1..5242880) { "Photo vide ou supérieure à 5 MiB." }
            val mime = application.contentResolver.getType(uri) ?: "application/octet-stream"
            val part =
                MultipartBody.Part.createFormData(
                    "file",
                    "photo",
                    bytes.toRequestBody(mime.toMediaType()),
                )
            _selected.postValue(
                repository.upload(row.id, part, caption.toRequestBody("text/plain".toMediaType()))
            )
        }
    }

    fun deletePhoto(photo: String) {
        val row = _selected.value ?: return
        work {
            repository.deletePhoto(row.id, photo)
            _selected.postValue(repository.detail(row.id).first)
        }
    }

    fun formError(message: String) {
        _message.value = message
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun work(action: suspend () -> Unit) {
        if (workJob?.isActive == true) return
        _busy.value = true
        _message.value = null
        workJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    _owner.postValue(repository.owner())
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure(e)
                } finally {
                    _busy.postValue(false)
                }
            }
    }

    private fun failure(e: Exception) {
        _message.postValue(AuthRepository.message(e))
        if (e is SessionExpired || e is AccountBlocked) _event.postValue(UiEvent("login"))
    }
}

class ListingViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ListingViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ListingViewModel(AppContainer.get(application).listings, application) as T
    }
}
