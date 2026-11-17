package com.iset.fastcolloc.data.remote

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class Session(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("expires_at") val expiresAt: Long? = null
) {
    fun normalized(now: Long = System.currentTimeMillis() / 1000) = copy(expiresAt = expiresAt ?: now + expiresIn)
    fun validFor(seconds: Long, now: Long = System.currentTimeMillis() / 1000) = (expiresAt ?: 0) > now + seconds
}
data class ProfileDto(val id: String, val firstName: String?, val lastName: String?, val gender: String?,
    val age: Int?, val role: String, val status: String, val completedAt: String?, val validatedSteps: List<Int> = emptyList())
data class DetailsDto(val profile: ProfileDto, val address: JsonObject, val personal: JsonObject,
    val contacts: JsonObject, val preferences: JsonObject, val preferredLocalities: List<Long>, val photos: List<PhotoDto>)
data class PhotoDto(val id: String, val caption: String?, @SerializedName("mime_type") val mimeType: String,
    @SerializedName("byte_size") val byteSize: Long)
data class CountryDto(val code: String, val name: String) { override fun toString() = name }
data class PlaceDto(val id: Long, val name: String) { override fun toString() = name }
data class IdentityDto(val firstName: String, val lastName: String, val gender: String?, val age: Int)
data class GeographyDto(val countryCode: String, val governorateId: Long, val localityId: Long, val street: String, val postalCode: String)
data class PersonalDto(val mainActivities: List<String>, val hobbies: List<String>, val nationalityCode: String?,
    val spokenLanguages: List<String>, val religion: String?, val smoking: Boolean?, val cleanliness: String?, val biography: String?)
data class ContactsDto(val mobilePhone: String, val residentialPhone: String, val callAvailability: String,
    val msnContact: String, val skypeContact: String, val websiteUrl: String)
data class PreferencesDto(val minBudget: Double?, val maxBudget: Double?, val moveInDate: String?, val localityIds: List<Long>)
data class IdentitySession(val id: String, val email: String, val emailConfirmed: Boolean)
data class SignedPhoto(val url: String, val expiresIn: Int)
data class GoogleUrl(val authorizationUrl: String)
data class LoginRequest(val email: String, val password: String)
data class EmailRequest(val email: String)
data class SignupRequest(val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class ExchangeRequest(val code: String, val codeVerifier: String)
data class VerifyRequest(val tokenHash: String, val type: String)
data class PasswordRequest(val password: String)
data class PhotoIds(val photoIds: List<String>)
data class CaptionRequest(val caption: String)
