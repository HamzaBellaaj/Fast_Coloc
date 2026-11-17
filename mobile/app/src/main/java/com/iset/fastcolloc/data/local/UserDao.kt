package com.iset.fastcolloc.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): UserEntity?

    @Query("SELECT * FROM users WHERE accountType != 'ADMIN' ORDER BY lastName COLLATE NOCASE, firstName COLLATE NOCASE")
    fun observeAllRegularUsers(): Flow<List<UserEntity>>

    @Query("UPDATE users SET isBlocked = :blocked WHERE id = :userId AND accountType != 'ADMIN'")
    suspend fun setBlocked(userId: Int, blocked: Boolean): Int

    @Query("DELETE FROM users WHERE id = :userId AND accountType != 'ADMIN'")
    suspend fun deleteRegularUser(userId: Int): Int

    @Query("SELECT COUNT(*) FROM users WHERE accountType = 'ADMIN'")
    suspend fun countAdmins(): Int
}
