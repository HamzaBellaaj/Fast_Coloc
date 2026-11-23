package com.iset.fastcolloc.viewmodel
import android.app.Application
import androidx.lifecycle.*
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.NotificationPreferencesDto
import kotlinx.coroutines.*
data class NotificationSettingsState(val preferences:NotificationPreferencesDto?=null,val enabled:Boolean=true,val loading:Boolean=false,val saving:Boolean=false,val saved:Boolean=false,val error:String?=null)
class NotificationSettingsViewModel(private val repository:NotificationDataSource,private val io:CoroutineDispatcher=Dispatchers.IO):ViewModel(){
 private val mutable=MutableLiveData(NotificationSettingsState());val state:LiveData<NotificationSettingsState> = mutable
 private var account:String?=null;val event=MutableLiveData<UiEvent<String>>()
 private fun update(change:(NotificationSettingsState)->NotificationSettingsState){mutable.value=change(mutable.value!!)}
 fun enabled(value:Boolean){update{it.copy(enabled=value,saved=false)}}
 fun load(){if(mutable.value!!.loading||mutable.value!!.saving)return;update{it.copy(loading=true,error=null)};viewModelScope.launch{try{val owner=withContext(io){repository.owner()};val preferences=withContext(io){repository.load()};if(withContext(io){repository.owner()}!=owner){clear();return@launch};account=owner;update{it.copy(preferences=preferences,enabled=preferences.newMessageEmail)}}catch(e:CancellationException){throw e}catch(e:Exception){failure(e)}finally{update{it.copy(loading=false)}}}}
 fun save(){val current=mutable.value!!;if(current.loading||current.saving||current.preferences==null||current.enabled==current.preferences.newMessageEmail)return;update{it.copy(saving=true,saved=false,error=null)};viewModelScope.launch{try{if(withContext(io){repository.owner()}!=account){clear();return@launch};val preferences=withContext(io){repository.save(current.enabled)};if(withContext(io){repository.owner()}!=account){clear();return@launch};update{it.copy(preferences=preferences,enabled=preferences.newMessageEmail,saved=true)}}catch(e:CancellationException){throw e}catch(e:Exception){failure(e)}finally{update{it.copy(saving=false)}}}}
 private fun clear(){account=null;mutable.value=NotificationSettingsState();event.value=UiEvent("login")}
 private fun failure(e:Exception){if(e is SessionExpired||e is AccountBlocked)clear() else update{it.copy(error=AuthRepository.message(e))}}
}
class NotificationSettingsFactory(private val application:Application):ViewModelProvider.Factory {override fun <T:ViewModel> create(modelClass:Class<T>):T{require(modelClass.isAssignableFrom(NotificationSettingsViewModel::class.java));@Suppress("UNCHECKED_CAST") return NotificationSettingsViewModel(AppContainer.get(application).notifications) as T}}
