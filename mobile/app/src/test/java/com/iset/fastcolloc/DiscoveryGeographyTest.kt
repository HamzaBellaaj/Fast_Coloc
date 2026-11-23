package com.iset.fastcolloc

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.*
import com.google.gson.Gson
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.local.*
import com.iset.fastcolloc.data.remote.*
import com.iset.fastcolloc.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryGeographyTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ListingRepository
    private val favorites = AtomicInteger()
    private var favoriteStatus = 204
    private val store = object : SessionStore {
        var current: Session? = Session("access", "refresh", 3600).normalized()
        override fun session() = current
        override fun save(value: Session) { current = value.normalized() }
        override fun clearSession() { current = null }
    }
    private val profiles = object : ProfileCacheDao {
        override fun observe(): LiveData<ProfileSummary?> = MutableLiveData()
        override suspend fun current() = ProfileSummary("reader", "Test", "Reader", true)
        override suspend fun save(value: ProfileSummary) = Unit
        override suspend fun clear() = Unit
    }
    private val cache = object : ListingCacheDao {
        var detail: CachedListingDetail? = null
        override fun observe(owner: String, key: String): LiveData<CachedListingPage?> = MutableLiveData()
        override suspend fun savePage(row: CachedListingPage) = Unit
        override suspend fun saveDetail(row: CachedListingDetail) { detail = row }
        override suspend fun detail(owner: String, id: String) = detail?.takeIf { it.id == id }
        override suspend fun clearPages() = Unit
        override suspend fun clearDetails() { detail = null }
        override suspend fun prunePages(oldest: Long) = Unit
        override suspend fun pruneDetails(oldest: Long) = Unit
    }
    private fun row(id: String, owner: String = "author") = """{
        "id":"$id","author_id":"$owner","kind":"OFFER","title":"Logement $id","description":"Une description réelle",
        "locality_id":1,"locality":"Tunis","governorate":"Tunis","street":"Rue de test","latitude":36.8,"longitude":10.18,
        "rent":400,"capacity":2,"property_type":"Appartement","roommate_type":"","equipment":[],"available_from":"2026-01-01",
        "publication_days":30,"status":"PUBLISHED","is_hidden":false,"expires_at":"2099-01-01T00:00:00Z","version":1,"favorite":false,"photos":[]}
        """.trimIndent()
    private fun response(code: Int = 200, body: String = "{}") = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").apply { if (code != 204) setBody(body) }
    @Before fun setup() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) { runnable.run() }
            override fun postToMainThread(runnable: Runnable) { runnable.run() }
            override fun isMainThread() = true
        })
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.contains("/favorite") == true -> { favorites.incrementAndGet(); response(favoriteStatus) }
                request.path?.startsWith("/api/v1/listings?") == true -> response(body = """{"items":[${row("own","reader")},${row("one")},${row("two")}],"total":3,"page":0,"size":20}""")
                request.path == "/api/v1/listings/one" -> response(body = row("one"))
                request.path?.startsWith("/api/v1/listings/map") == true -> response(body = """{"items":[${row("one")},${row("two").replace("36.8", "0")}],"total":2,"page":0,"size":50}""")
                request.path == "/api/v1/geography/position" -> response(400, """{"message":"Position dans l’eau","code":"INVALID_POSITION"}""")
                else -> response(404)
            }
        }
        server.start()
        repository = ListingRepository(AuthRepository(ApiClient.create(store,server.url("/api/v1/").toString()),store,profiles),cache)
    }
    @After fun cleanup() { server.shutdown(); Dispatchers.resetMain(); ArchTaskExecutor.getInstance().setDelegate(null) }
    private fun await(condition: () -> Boolean) = runBlocking {
        val until = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < until) delay(10)
        assertTrue("State transition timed out", condition())
    }
    @Test fun rightSwipeUsesIdempotentFavoriteOnceAndNetworkFailureKeepsCard() {
        val model = DiscoveryViewModel(repository)
        model.open(); await { !model.state.value!!.loading }
        assertEquals(listOf("one","two"), model.state.value!!.items.map { it.id })
        favoriteStatus = 500
        model.decide(true); model.decide(true)
        await { !model.state.value!!.acting }
        assertEquals(1,favorites.get())
        assertEquals("one",model.state.value!!.items.first().id)
        favoriteStatus = 204
        model.decide(true); await { !model.state.value!!.acting }
        assertEquals("two",model.state.value!!.items.first().id)
        model.decide(false); await { !model.state.value!!.acting && !model.state.value!!.loading }
        assertEquals(2,favorites.get()) // left/pass issued no favorite request
        model.refresh(); await { !model.state.value!!.loading }
        assertTrue(model.state.value!!.items.isEmpty()) // processed cards do not reappear
        assertTrue((0 until server.requestCount).map { server.takeRequest() }.filter { it.path?.contains("/favorite") == true }.all { it.method == "PUT" })
    }
    @Test fun mapUsesDedicatedContractPreservesFiltersAndSkipsInvalidCoordinates() {
        val model = MapViewModel(repository)
        model.load(mapOf("maxRent" to "450","localityId" to "1"))
        await { !model.state.value!!.loading }
        assertEquals(listOf("one"),model.state.value!!.items.map { it.id })
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("/listings/map?"))
        assertEquals("450",request.requestUrl!!.queryParameter("maxRent"))
        assertEquals("1",request.requestUrl!!.queryParameter("localityId"))
    }
    @Test fun rejectedWaterPointRestoresPreviousCoordinates() {
        val model = LocationViewModel(repository)
        model.initial(36.8,10.18,"Rue initiale")
        model.validate(36.822,10.249)
        await { !model.state.value!!.loading }
        assertEquals(36.8,model.state.value!!.position!!.latitude,0.0)
        assertTrue(model.state.value!!.restore)
        assertEquals("Position dans l’eau",model.state.value!!.message)
    }
    @Test fun hiddenExpiredAndCoordinateFreeOffersCannotBecomeSwipeOrMapCandidates() {
        val listing = Gson().fromJson(row("one"),ListingDto::class.java)
        assertFalse(DiscoveryViewModel.available(listing.copy(isHidden = true)))
        assertFalse(DiscoveryViewModel.available(listing.copy(expiresAt = "2000-01-01T00:00:00Z")))
        val marker = Gson().fromJson(row("one"),ListingMarkerDto::class.java)
        assertFalse(MapViewModel.valid(marker.copy(latitude = null)))
        assertFalse(MapViewModel.valid(marker.copy(isHidden = true)))
    }
}
