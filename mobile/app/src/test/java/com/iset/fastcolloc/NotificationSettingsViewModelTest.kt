package com.iset.fastcolloc
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.iset.fastcolloc.data.NotificationDataSource
import com.iset.fastcolloc.data.remote.NotificationPreferencesDto
import com.iset.fastcolloc.viewmodel.NotificationSettingsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
 private val dispatcher=StandardTestDispatcher()
 @Before fun before(){Dispatchers.setMain(dispatcher);ArchTaskExecutor.getInstance().setDelegate(object:TaskExecutor(){override fun executeOnDiskIO(runnable:Runnable){runnable.run()};override fun postToMainThread(runnable:Runnable){runnable.run()};override fun isMainThread()=true})}
 @After fun after(){ArchTaskExecutor.getInstance().setDelegate(null);Dispatchers.resetMain()}
 private class Fake:NotificationDataSource{var account="first";var enabled=true;var fail=false;var calls=0;override suspend fun owner()=account;override suspend fun load()=NotificationPreferencesDto(enabled,true,"local");override suspend fun save(enabled:Boolean):NotificationPreferencesDto{calls++;if(fail)throw java.io.IOException("Offline");this.enabled=enabled;return load()}}
 @Test fun loadsAndSavesSharedPreference()=runTest(dispatcher){val api=Fake();val vm=NotificationSettingsViewModel(api,dispatcher);vm.load();advanceUntilIdle();assertEquals("local",vm.state.value!!.preferences!!.deliveryMode);vm.enabled(false);vm.save();advanceUntilIdle();assertFalse(api.enabled);assertTrue(vm.state.value!!.saved)}
 @Test fun failedSaveKeepsTheChoiceForRetry()=runTest(dispatcher){val api=Fake();val vm=NotificationSettingsViewModel(api,dispatcher);vm.load();advanceUntilIdle();api.fail=true;vm.enabled(false);vm.save();advanceUntilIdle();assertFalse(vm.state.value!!.enabled);assertTrue(vm.state.value!!.preferences!!.newMessageEmail);assertNotNull(vm.state.value!!.error);api.fail=false;vm.save();advanceUntilIdle();assertTrue(vm.state.value!!.saved)}
 @Test fun accountChangeDoesNotWriteToAnotherUser()=runTest(dispatcher){val api=Fake();val vm=NotificationSettingsViewModel(api,dispatcher);vm.load();advanceUntilIdle();api.account="second";vm.enabled(false);vm.save();advanceUntilIdle();assertEquals(0,api.calls);assertNull(vm.state.value!!.preferences);assertEquals("login",vm.event.value!!.consume())}
}
