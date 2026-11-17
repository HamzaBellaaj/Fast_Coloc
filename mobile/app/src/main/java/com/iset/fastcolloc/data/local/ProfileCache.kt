package com.iset.fastcolloc.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*

/** Non-sensitive server summary only: never credentials, contacts, tokens or authority. */
@Entity(tableName = "profile_summary")
data class ProfileSummary(@PrimaryKey val id: String, val firstName: String?, val lastName: String?, val completed: Boolean)
@Dao interface ProfileCacheDao {
    @Query("SELECT * FROM profile_summary LIMIT 1") fun observe(): LiveData<ProfileSummary?>
    @Query("SELECT * FROM profile_summary LIMIT 1") suspend fun current(): ProfileSummary?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(value: ProfileSummary)
    @Query("DELETE FROM profile_summary") suspend fun clear()
    @Transaction suspend fun replace(value: ProfileSummary) { clear(); save(value) }
}
@Database(entities = [ProfileSummary::class], version = 1, exportSchema = false)
abstract class ProfileCache : RoomDatabase() {
    abstract fun profiles(): ProfileCacheDao
    companion object {
        @Volatile private var instance: ProfileCache? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ProfileCache::class.java, "fastcoloc_server_cache")
                .build().also { instance = it }
        }
    }
}
