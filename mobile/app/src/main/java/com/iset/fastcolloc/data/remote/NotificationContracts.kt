package com.iset.fastcolloc.data.remote
data class NotificationPreferencesDto(val newMessageEmail:Boolean,val deliveryAvailable:Boolean,val deliveryMode:String)
data class NotificationPreferencesPatch(val newMessageEmail:Boolean)
