package com.iset.fastcolloc.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*

/** Chap.6 / TP6. These are downloaded snapshots, never an independent listing database. */
@Entity(tableName = "listing_pages", primaryKeys = ["owner", "cacheKey"])
data class CachedListingPage(
    val owner: String,
    val cacheKey: String,
    val body: String,
    val savedAt: Long,
)

@Entity(tableName = "listing_details", primaryKeys = ["owner", "id"])
data class CachedListingDetail(
    val owner: String,
    val id: String,
    val body: String,
    val savedAt: Long,
)

@Dao
interface ListingCacheDao {
    @Query("SELECT * FROM listing_pages WHERE owner=:owner AND cacheKey=:key")
    fun observe(owner: String, key: String): LiveData<CachedListingPage?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePage(row: CachedListingPage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDetail(row: CachedListingDetail)

    @Query("SELECT * FROM listing_details WHERE owner=:owner AND id=:id")
    suspend fun detail(owner: String, id: String): CachedListingDetail?

    @Query("DELETE FROM listing_pages") suspend fun clearPages()

    @Query("DELETE FROM listing_details") suspend fun clearDetails()

    @Transaction
    suspend fun clear() {
        clearPages()
        clearDetails()
    }

    @Query("DELETE FROM listing_pages WHERE savedAt < :oldest") suspend fun prunePages(oldest: Long)

    @Query("DELETE FROM listing_details WHERE savedAt < :oldest")
    suspend fun pruneDetails(oldest: Long)
}

@Database(
    entities = [CachedListingPage::class, CachedListingDetail::class],
    version = 1,
    exportSchema = false,
)
abstract class ListingCache : RoomDatabase() {
    abstract fun listings(): ListingCacheDao

    companion object {
        @Volatile private var instance: ListingCache? = null

        fun get(context: Context) =
            instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                ListingCache::class.java,
                                "fastcoloc_listing_cache",
                            )
                            .build()
                            .also { instance = it }
                }
    }
}
