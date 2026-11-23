package com.iset.fastcolloc.data
import com.iset.fastcolloc.data.remote.*
interface NotificationDataSource {suspend fun owner():String;suspend fun load():NotificationPreferencesDto;suspend fun save(enabled:Boolean):NotificationPreferencesDto}
class NotificationRepository(private val auth:AuthRepository):NotificationDataSource {
 override suspend fun owner()=auth.accountId()
 override suspend fun load()=auth.authorized { notificationPreferences() }
 override suspend fun save(enabled:Boolean)=auth.authorized { updateNotificationPreferences(NotificationPreferencesPatch(enabled)) }
}
