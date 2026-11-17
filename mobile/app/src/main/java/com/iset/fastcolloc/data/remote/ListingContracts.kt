package com.iset.fastcolloc.data.remote

import com.google.gson.annotations.SerializedName

// Chap.1 (data classes/null safety), chap.7 + TP7 (exact JSON mapping).
data class ListingPhotoDto(val id: String, val caption: String, val url: String?)

data class ListingDto(
    val id: String,
    @SerializedName("author_id") val authorId: String,
    val kind: String,
    val title: String,
    val description: String?,
    @SerializedName("locality_id") val localityId: Long?,
    val locality: String,
    val governorate: String,
    val street: String?,
    val latitude: Double,
    val longitude: Double,
    val rent: Double,
    val capacity: Int,
    @SerializedName("property_type") val propertyType: String,
    @SerializedName("roommate_type") val roommateType: String,
    val equipment: List<String>,
    @SerializedName("available_from") val availableFrom: String,
    @SerializedName("available_until") val availableUntil: String?,
    @SerializedName("publication_days") val publicationDays: Int?,
    val status: String,
    @SerializedName("expires_at") val expiresAt: String?,
    val version: Long?,
    val favorite: Boolean,
    val photos: List<ListingPhotoDto>,
)

data class ListingPageDto(
    val items: List<ListingDto>,
    val total: Long,
    val page: Int,
    val size: Int,
)

data class ListingAreaDto(
    val id: Long,
    val locality: String,
    @SerializedName("governorate_id") val governorateId: Long,
    val governorate: String,
) {
    override fun toString() = "$governorate · $locality"
}

data class ListingWriteDto(
    val kind: String,
    val title: String,
    val description: String,
    val localityId: Long,
    val street: String,
    val latitude: Double,
    val longitude: Double,
    val rent: Double,
    val capacity: Int,
    val propertyType: String,
    val roommateType: String,
    val equipment: List<String>,
    val availableFrom: String,
    val availableUntil: String?,
    val publicationDays: Int,
    val version: Long? = null,
)
