package com.iset.fastcolloc.viewmodel
import android.app.Application
import androidx.lifecycle.*
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.*
import kotlinx.coroutines.*
import java.util.UUID

data class MessagingState(
 val conversations:List<ConversationDto> = emptyList(),val selected:ConversationDto?=null,
 val messages:List<PrivateMessageDto> = emptyList(),val ownerId:String="",val listingId:String?=null,
 val listingTitle:String="",val draft:String="",val loading:Boolean=false,val sending:Boolean=false,
 val hasMore:Boolean=false,val moreInbox:Boolean=false,val error:String?=null,
)
class MessagingViewModel(private val repository:MessagingDataSource,private val io:CoroutineDispatcher=Dispatchers.IO):ViewModel() {
 private val mutable=MutableLiveData(MessagingState());val state:LiveData<MessagingState> = mutable
 private var started=false;private var generation=0;private var page=0;private var readThrough=0L
 private var openingConversationId:String?=null
 private var refreshJob:Job?=null
 private var pending:Pair<String,String>?=null
 val event=MutableLiveData<UiEvent<String>>()
 private fun update(change:(MessagingState)->MessagingState){mutable.value=change(mutable.value!!)}
 fun open(conversationId:String?,listingId:String?,title:String){if(started)return;started=true;openingConversationId=conversationId;update{it.copy(listingId=listingId,listingTitle=title)};refresh()}
 fun draft(value:String){update{it.copy(draft=value)}}
 fun refresh(openId:String?=null,background:Boolean=false){
  val current=mutable.value!!;if(current.loading||current.sending||refreshJob?.isActive==true)return;if(openId!=null)openingConversationId=openId;val g=generation;if(!background)update{it.copy(loading=true)}
  refreshJob=viewModelScope.launch { try {
   val owner=withContext(io){repository.owner()};if(g!=generation)return@launch
   if(current.ownerId.isNotEmpty()&&owner!=current.ownerId){clearForLogin();return@launch}
   val inbox=withContext(io){repository.inbox()};if(g!=generation)return@launch
   val id=openingConversationId?:current.selected?.id?:current.listingId?.let { listing->withContext(io){repository.lookup(listing)}?.id }
   var selected=current.selected;var messages=current.messages;var hasMore=current.hasMore
   if(id!=null){
    selected=withContext(io){repository.detail(id)}
    val history=withContext(io){repository.history(id)}
    if(g!=generation)return@launch
    messages=(messages+history.items).distinctBy{it.id}.sortedBy{it.id};if(messages.size==history.items.size)hasMore=history.hasMore
    history.items.lastOrNull()?.id?.let { newest->if(newest>readThrough){withContext(io){repository.read(id,newest)};readThrough=newest} }
   }
   if(g!=generation||withContext(io){repository.owner()}!=owner)return@launch
   update{it.copy(ownerId=owner,conversations=if(page==0)inbox.items else (inbox.items+it.conversations).distinctBy{row->row.id},selected=selected,messages=messages,hasMore=hasMore,moreInbox=if(page==0)inbox.total>inbox.items.size else it.moreInbox,error=if(background)it.error else null)}
  }catch(e:CancellationException){throw e}catch(e:Exception){failure(e)}finally{if(g==generation&&!background)update{it.copy(loading=false)}} }
 }
 fun older(){val current=mutable.value!!;val id=current.selected?.id?:return;val before=current.messages.firstOrNull()?.id?:return;if(current.loading||current.sending)return;val g=generation;update{it.copy(loading=true)}
  viewModelScope.launch{try{val history=withContext(io){repository.history(id,before)};if(g==generation)update{it.copy(messages=(history.items+it.messages).distinctBy{m->m.id}.sortedBy{m->m.id},hasMore=history.hasMore)}}catch(e:CancellationException){throw e}catch(e:Exception){failure(e)}finally{if(g==generation)update{it.copy(loading=false)}}}}
 fun more(){val current=mutable.value!!;if(current.loading||current.sending)return;val g=generation;update{it.copy(loading=true)}
  viewModelScope.launch{try{val result=withContext(io){repository.inbox(page+1)};if(g==generation){page++;update{it.copy(conversations=(it.conversations+result.items).distinctBy{c->c.id},moreInbox=(page+1)*result.size<result.total)}}}catch(e:CancellationException){throw e}catch(e:Exception){failure(e)}finally{if(g==generation)update{it.copy(loading=false)}}}}
 fun send(){val current=mutable.value!!;val content=current.draft.trim();if(current.sending||current.loading||content.isEmpty()||content.length>4000||(current.selected==null&&current.listingId==null))return
  refreshJob?.cancel()
  val request=pending?.takeIf{it.first==content}?:Pair(content,UUID.randomUUID().toString());pending=request;val g=generation;update{it.copy(sending=true,error=null)}
  viewModelScope.launch{try{withTimeout(20_000){
   val selected=current.selected?:withContext(io){repository.start(current.listingId!!,content,request.second)}
   if(current.selected!=null)withContext(io){repository.send(selected.id,content,request.second)}
   if(g!=generation||withContext(io){repository.owner()}!=current.ownerId)return@withTimeout
   pending=null;update{it.copy(selected=selected,draft="",sending=false)};refresh()
  }}catch(e:TimeoutCancellationException){if(g==generation)update{it.copy(error="Le serveur ne confirme pas l’envoi. Réessaie : ton brouillon est conservé.")}}catch(e:CancellationException){throw e}catch(e:Exception){failure(e)}finally{if(g==generation)update{it.copy(sending=false)}}}
 }
 private fun clearForLogin(){generation++;pending=null;openingConversationId=null;mutable.value=MessagingState();event.value=UiEvent("login")}
 private fun failure(e:Exception){if(e is SessionExpired||e is AccountBlocked){clearForLogin();return};update{it.copy(error=AuthRepository.message(e))}}
}
class MessagingViewModelFactory(private val application:Application):ViewModelProvider.Factory {
 override fun <T:ViewModel> create(modelClass:Class<T>):T {require(modelClass.isAssignableFrom(MessagingViewModel::class.java));@Suppress("UNCHECKED_CAST") return MessagingViewModel(AppContainer.get(application).messaging) as T}
}
