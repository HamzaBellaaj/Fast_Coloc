package com.iset.fastcolloc.ui.messaging
import com.iset.fastcolloc.ui.showBusy
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
   binding.saveNotifications.showBusy(result.saving)
   binding.saveNotifications.isEnabled=result.preferences!=null&&result.enabled!=result.preferences.newMessageEmail&&!result.loading&&!result.saving
   binding.reloadNotifications.isEnabled=!result.loading&&!result.saving
   binding.notificationState.compoundDrawablePadding=(8*resources.displayMetrics.density).toInt()
   binding.notificationState.setCompoundDrawablesRelativeWithIntrinsicBounds(when { result.error!=null->R.drawable.ic_fc_alert;result.loading||result.saving->R.drawable.ic_fc_clock;result.saved->R.drawable.ic_fc_success;result.preferences?.deliveryMode=="local"||result.preferences?.deliveryAvailable==false->R.drawable.ic_fc_warning;else->0 },0,0,0)
   binding.notificationState.text=result.error?:when{result.loading->"Chargement…";result.saving->"Enregistrement…";result.saved->getString(R.string.notification_saved);result.preferences?.deliveryMode=="local"->getString(R.string.notification_local);result.preferences?.deliveryAvailable==false->getString(R.string.notification_disabled);else->""}
  };if(settings.state.value?.preferences==null)settings.load()
 }
}
