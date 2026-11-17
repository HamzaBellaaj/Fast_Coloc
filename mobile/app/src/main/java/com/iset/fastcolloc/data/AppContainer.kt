package com.iset.fastcolloc.data

import android.content.Context
import com.iset.fastcolloc.data.local.ProfileCache
import com.iset.fastcolloc.data.remote.ApiClient

class AppContainer private constructor(private val application: android.app.Application) {
    val sessions = SessionManager(application)
    private val listingCache = com.iset.fastcolloc.data.local.ListingCache.get(application).listings()
    val repository = AuthRepository(ApiClient.create(sessions), sessions, ProfileCache.get(application).profiles()) { listingCache.clear() }
    val listings = ListingRepository(repository,listingCache)
    val oauth = OAuthCoordinator(application, repository)
    suspend fun prepareLegacy() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if(application.getDatabasePath("fastcolloc_database").exists())
            com.iset.fastcolloc.data.local.AppDatabase.getDatabase(application).openHelper.writableDatabase
        Unit
    }
    companion object {
        @Volatile private var instance: AppContainer? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: AppContainer(context.applicationContext as android.app.Application).also { instance = it }
        }
    }
}
