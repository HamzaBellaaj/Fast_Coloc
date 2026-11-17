package com.iset.fastcolloc.data

import com.iset.fastcolloc.data.remote.Session

interface SessionStore {
    fun session(): Session?
    fun save(value: Session)
    fun clearSession()
    fun hasValidSession(): Boolean = session() != null
}
