package com.iset.fastcolloc
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.remote.*
import com.iset.fastcolloc.viewmodel.MessagingViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
@OptIn(ExperimentalCoroutinesApi::class)
class MessagingViewModelTest {
 private val dispatcher=StandardTestDispatcher()
 @Before fun before(){Dispatchers.setMain(dispatcher);ArchTaskExecutor.getInstance().setDelegate(object:TaskExecutor(){override fun executeOnDiskIO(runnable:Runnable){runnable.run()};override fun postToMainThread(runnable:Runnable){runnable.run()};override fun isMainThread()=true})}
 @After fun after(){ArchTaskExecutor.getInstance().setDelegate(null);Dispatchers.resetMain()}
 private class Fake:MessagingDataSource {
  val conversation=ConversationDto("chat","listing","listing","Chambre","OFFER","Ariana",450.0,"Ahmed","owner","Bonjour",1,true)
  var account="guest";var fail=false;var starts=0;val keys=mutableListOf<String>();val messages=mutableListOf(PrivateMessageDto(2,"chat","owner","initial","Bonjour","2026-11-23T12:00:00Z"))
  override suspend fun owner()=account
  override suspend fun inbox(page:Int)=ConversationPageDto(listOf(conversation),1,page,20)
  override suspend fun lookup(listingId:String):ConversationDto?=null
  override suspend fun detail(id:String)=conversation
  override suspend fun history(id:String,before:Long?)=if(before==null)MessagePageDto(messages.toList(),true,2) else MessagePageDto(listOf(PrivateMessageDto(1,"chat","owner","older","Ancien message","2026-11-23T11:00:00Z")),false,1)
  override suspend fun start(listingId:String,content:String,clientId:String):ConversationDto{starts++;send("chat",content,clientId);return conversation}
  override suspend fun send(id:String,content:String,clientId:String):PrivateMessageDto{keys+=clientId;if(fail)throw java.io.IOException("Offline");val message=PrivateMessageDto(3,id,account,clientId,content,"2026-11-23T12:01:00Z");messages+=message;return message}
  override suspend fun read(id:String,throughId:Long){}
 }
 @Test fun failedSendKeepsDraftAndRetriesSameId()=runTest(dispatcher){val api=Fake();val vm=MessagingViewModel(api,dispatcher);vm.open("chat",null,"");advanceUntilIdle();vm.draft("Une visite ?");api.fail=true;vm.send();advanceUntilIdle();assertEquals("Une visite ?",vm.state.value!!.draft);assertNotNull(vm.state.value!!.error);api.fail=false;vm.send();advanceUntilIdle();assertEquals(api.keys[0],api.keys[1]);assertEquals("",vm.state.value!!.draft);assertEquals(2,vm.state.value!!.messages.size)}
 @Test fun contactDoesNotCreateUntilFirstMessage()=runTest(dispatcher){val api=Fake();val vm=MessagingViewModel(api,dispatcher);vm.open(null,"listing","Chambre");advanceUntilIdle();assertEquals(0,api.starts);vm.draft("Bonjour");vm.send();advanceUntilIdle();assertEquals(1,api.starts);assertEquals("chat",vm.state.value!!.selected!!.id)}
 @Test fun pollingPreservesLoadedOlderMessages()=runTest(dispatcher){val api=Fake();val vm=MessagingViewModel(api,dispatcher);vm.open("chat",null,"");advanceUntilIdle();vm.older();advanceUntilIdle();vm.refresh();advanceUntilIdle();assertEquals(listOf(1L,2L),vm.state.value!!.messages.map{it.id});assertFalse(vm.state.value!!.hasMore)}
 @Test fun accountChangeClearsPrivateHistoryAndDraft()=runTest(dispatcher){val api=Fake();val vm=MessagingViewModel(api,dispatcher);vm.open("chat",null,"");advanceUntilIdle();vm.draft("Private draft");api.account="another-account";vm.refresh();advanceUntilIdle();assertTrue(vm.state.value!!.messages.isEmpty());assertTrue(vm.state.value!!.conversations.isEmpty());assertEquals("",vm.state.value!!.draft);assertEquals("login",vm.event.value!!.consume())}
 @Test fun emptyAndOversizedMessagesAreNotSent()=runTest(dispatcher){val api=Fake();val vm=MessagingViewModel(api,dispatcher);vm.open("chat",null,"");advanceUntilIdle();vm.draft("   ");vm.send();vm.draft("x".repeat(4001));vm.send();advanceUntilIdle();assertTrue(api.keys.isEmpty())}
}
