package com.iset.fastcolloc.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lastName: String,
    val firstName: String,
    val email: String,
    val role: String,
    val governorate: String,
    val city: String,
    val preferredZone: String,
    val birthDate: String,
    val status: String,
    val gender: String,
    val smoker: Boolean,
    val pets: Boolean,
    val phone: String,
    val preferredContact: String,
    val photoUri: String?,
    val photoDescription: String?,
    @ColumnInfo(defaultValue = "'USER'") val accountType: String = "USER",
    @ColumnInfo(defaultValue = "0") val isBlocked: Boolean = false
)