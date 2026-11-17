package com.iset.fastcolloc.data

import android.content.Context
import com.google.gson.Gson
import com.iset.fastcolloc.data.remote.Session

class SessionManager(context: Context) : SessionStore {
    private val store = SecureStore(context.applicationContext)
    @Volatile private var current: Session? = runCatching {
        store.read("session")?.let { Gson().fromJson(it, Session::class.java) }
    }.getOrNull()
    init { context.getSharedPreferences("fastcolloc_session", Context.MODE_PRIVATE).edit().clear().apply() }
    @Synchronized override fun session(): Session? = current
    @Synchronized override fun save(value: Session) {
        require(value.accessToken.isNotBlank() && value.refreshToken.isNotBlank())
        val normalized = value.normalized()
        store.write("session", Gson().toJson(normalized))
        current = normalized
    }
    @Synchronized override fun clearSession() { current = null; store.delete("session"); store.delete("oauth") }
    // Legacy administration is unreachable and cannot grant privileges.
    fun getAccountType(): String? = null
    fun getUserId(): Int = -1
    fun getEmail(): String? = null
    fun getRole(): String? = null
}
