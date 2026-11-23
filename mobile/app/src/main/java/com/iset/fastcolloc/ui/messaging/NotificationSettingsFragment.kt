package com.iset.fastcolloc.ui.messaging
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentNotificationSettingsBinding
import com.iset.fastcolloc.ui.auth.BoundFragment
import com.iset.fastcolloc.viewmodel.*
class NotificationSettingsFragment:BoundFragment<FragmentNotificationSettingsBinding>(FragmentNotificationSettingsBinding::inflate){
 private val settings:NotificationSettingsViewModel by viewModels{NotificationSettingsFactory(requireActivity().application)}
 override fun onViewCreated(view:View,state:Bundle?){var rendering=false
  binding.messageEmailSwitch.setOnCheckedChangeListener{_,value->if(!rendering)settings.enabled(value)}
  binding.saveNotifications.setOnClickListener{settings.save()};binding.reloadNotifications.setOnClickListener{settings.load()}
  settings.event.observe(viewLifecycleOwner){it.consume()?.let{findNavController().navigate(R.id.loginFragment)}}
  settings.state.observe(viewLifecycleOwner){result->rendering=true;binding.messageEmailSwitch.isChecked=result.enabled;rendering=false
   binding.messageEmailSwitch.isEnabled=result.preferences!=null&&!result.loading&&!result.saving
   binding.saveNotifications.isEnabled=result.preferences!=null&&result.enabled!=result.preferences.newMessageEmail&&!result.loading&&!result.saving
   binding.reloadNotifications.isEnabled=!result.loading&&!result.saving
   binding.notificationState.text=result.error?:when{result.loading->"Chargement…";result.saving->"Enregistrement…";result.saved->getString(R.string.notification_saved);result.preferences?.deliveryMode=="local"->getString(R.string.notification_local);result.preferences?.deliveryAvailable==false->getString(R.string.notification_disabled);else->""}
  };if(settings.state.value?.preferences==null)settings.load()
 }
}
