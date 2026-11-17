package com.iset.fastcolloc

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.local.*
import com.iset.fastcolloc.data.remote.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private val store = object : SessionStore {
        @Volatile var value: Session? = null
        override fun session() = value
        override fun save(value: Session) { this.value = value.normalized() }
        override fun clearSession() { value = null }
    }
    private val cache = object : ProfileCacheDao {
        var cleared = false
        override fun observe(): LiveData<ProfileSummary?> = MutableLiveData()
        override suspend fun current(): ProfileSummary? = null
        override suspend fun save(value: ProfileSummary) = Unit
        override suspend fun clear() { cleared = true }
    }
    private lateinit var repo: AuthRepository
    @Before fun setup() {
        server = MockWebServer(); server.start()
        repo = AuthRepository(ApiClient.create(store, server.url("/api/v1/").toString()), store, cache)
    }
    @After fun stop() { server.shutdown() }
    private fun response(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    @Test fun refreshIsSerializedAndRotatedTokenIsUsedByConcurrentRequests() = runBlocking {
        store.save(Session("old-access", "old-refresh", 0, 1))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if(request.path == "/api/v1/auth/refresh")
                response(200, """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}""")
            else response(200, """{"id":"user","email":"test@example.invalid","emailConfirmed":true}""")
        }
        coroutineScope { (1..8).map { async(Dispatchers.Default) { repo.authorized { session() } } }.awaitAll() }
        val requests = (1..9).map { server.takeRequest() }
        assertEquals(1, requests.count { it.path == "/api/v1/auth/refresh" })
        assertNull(requests.first { it.path == "/api/v1/auth/refresh" }.getHeader("Authorization"))
        assertTrue(requests.filter { it.path != "/api/v1/auth/refresh" }.all { it.getHeader("Authorization") == "Bearer new-access" })
        assertEquals("new-refresh", store.session()!!.refreshToken)
    }
    @Test fun deniedRefreshClearsSessionAndCache() = runBlocking {
        store.save(Session("old", "refresh", 0, 1)); server.enqueue(response(400,"{}"))
        try { repo.authorized { session() }; fail("Expected expiration") } catch (_: SessionExpired) { }
        assertNull(store.session()); assertTrue(cache.cleared)
    }
    @Test fun unauthorizedRequestIsRetriedOnlyOnce() = runBlocking {
        store.save(Session("old", "refresh", 3600))
        server.enqueue(response(401,"{}")); server.enqueue(response(200,"""{"access_token":"new","refresh_token":"rotated","expires_in":3600}""")); server.enqueue(response(401,"{}"))
        try { repo.authorized { session() }; fail("Expected expiration") } catch (_: SessionExpired) { }
        assertEquals(3,server.requestCount); assertNull(store.session())
    }
    @Test fun blockedAccountClearsSession() = runBlocking {
        store.save(Session("access", "refresh", 3600)); server.enqueue(response(403,"""{"code":"ACCOUNT_BLOCKED"}"""))
        try { repo.authorized { details() }; fail("Expected block") } catch (_: AccountBlocked) { }
        assertNull(store.session()); assertTrue(cache.cleared)
    }
    @Test fun passwordLimitCountsUtf8Bytes() {
        AuthRepository.validatePassword("a".repeat(12))
        for(value in listOf("short", "é".repeat(37))) {
            try { AuthRepository.validatePassword(value); fail("Expected invalid password") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun contractsPreserveServerIdentifiersAndNullablePersonalChoices() {
        val dto = Gson().fromJson("""{"id":"photo-id","caption":null,"mime_type":"image/png","byte_size":12}""", PhotoDto::class.java)
        assertEquals("photo-id",dto.id); assertEquals("image/png",dto.mimeType)
        val personal = PersonalDto(emptyList(),emptyList(),null,emptyList(),null,null,null,null)
        assertNull(personal.smoking)
    }
}
