package com.iset.fastcolloc.data

import com.google.gson.Gson
import com.iset.fastcolloc.data.local.*
import com.iset.fastcolloc.data.remote.*
import java.io.IOException
import okhttp3.MultipartBody
import okhttp3.RequestBody

/** Chap.6 + chap.7 / TP6 + TP7: remote authority, account-scoped Room cache. */
class ListingRepository(private val auth: AuthRepository, private val cache: ListingCacheDao) {
    private val json = Gson()

    suspend fun owner() = auth.accountId()

    fun key(mode: String, query: Map<String, String>) = mode + json.toJson(query.toSortedMap())

    fun observe(owner: String, key: String) = cache.observe(owner, key)

    fun decode(row: CachedListingPage): ListingPageDto? {
        val age = System.currentTimeMillis() - row.savedAt
        if (age > 24 * 60 * 60 * 1000L) return null
        val value = json.fromJson(row.body, ListingPageDto::class.java)
        return if (age < 300000) value
        else
            value.copy(
                items =
                    value.items.map { r -> r.copy(photos = r.photos.map { it.copy(url = null) }) }
            )
    }

    suspend fun refresh(mode: String, query: Map<String, String>) {
        val owner = owner()
        val value =
            auth.authorized {
                when (mode) {
                    "mine" -> myListings(query)
                    "favorites" -> favoriteListings(query)
                    else -> listings(query)
                }
            }
        if (runCatching { owner() }.getOrNull() != owner)
            throw kotlinx.coroutines.CancellationException("Account changed")
        cache.savePage(
            CachedListingPage(
                owner,
                key(mode, query),
                json.toJson(value),
                System.currentTimeMillis(),
            )
        )
        val oldest = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        cache.prunePages(oldest)
        cache.pruneDetails(oldest)
    }

    suspend fun estimate(logement: RequestBody, photos: List<MultipartBody.Part>) =
        auth.authorized { estimateRent(logement, photos) }

    suspend fun catalog(query: Map<String,String>) = auth.authorized { listings(query) }
    suspend fun map(query: Map<String,String>) = auth.authorized { listingMap(query) }
    suspend fun searchAddress(query: String) = auth.authorized { searchAddress(query) }
    suspend fun validatePosition(point: GeoCoordinatesDto) = auth.authorized { validatePosition(point) }
    suspend fun interested(id: String) {
        auth.authorized { favoriteListing(id) } // idempotent PUT; never toggle a previously saved favorite
        cache.clearPages()
    }
    suspend fun areas() = auth.authorized { listingAreas() }
    suspend fun recommendations() = auth.authorized { aiRecommendations() }

    suspend fun detail(id: String): Pair<ListingDto, Boolean> {
        val owner = owner()
        try {
            val value = auth.authorized { listing(id) }
            if (runCatching { owner() }.getOrNull() != owner)
                throw kotlinx.coroutines.CancellationException("Account changed")
            cache.saveDetail(
                CachedListingDetail(owner, id, json.toJson(value), System.currentTimeMillis())
            )
            return value to false
        } catch (e: IOException) {
            if (e is SessionExpired || e is AccountBlocked) throw e
            val row = cache.detail(owner, id) ?: throw e
            if (System.currentTimeMillis() - row.savedAt > 24 * 60 * 60 * 1000L) throw e
            val value = json.fromJson(row.body, ListingDto::class.java)
            return value.copy(photos = value.photos.map { it.copy(url = null) }) to true
        }
    }

    suspend fun save(id: String?, body: ListingWriteDto) =
        auth.authorized { if (id == null) createListing(body) else updateListing(id, body) }

    suspend fun publish(row: ListingDto) =
        auth.authorized {
            if (row.status == "DRAFT") publishListing(row.id) else renewListing(row.id)
        }

    suspend fun delete(id: String) {
        auth.authorized { deleteListing(id) }
        cache.clear()
    }

    suspend fun favorite(row: ListingDto) {
        auth.authorized { if (row.favorite) unfavoriteListing(row.id) else favoriteListing(row.id) }
        cache.clearPages()
    }

    suspend fun upload(id: String, file: MultipartBody.Part, caption: RequestBody) =
        auth.authorized { uploadListingPhoto(id, file, caption) }

    suspend fun deletePhoto(id: String, photo: String) =
        auth.authorized { deleteListingPhoto(id, photo) }
}
