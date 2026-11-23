package com.iset.fastcolloc.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface FastColocApi {
    @GET("notification-preferences") suspend fun notificationPreferences(): NotificationPreferencesDto
    @PATCH("notification-preferences") suspend fun updateNotificationPreferences(@Body body:NotificationPreferencesPatch): NotificationPreferencesDto
    @GET("conversations") suspend fun conversations(@Query("page") page:Int=0,@Query("listingId") listingId:String?=null): ConversationPageDto
    @GET("conversations/{id}") suspend fun conversation(@Path("id") id:String): ConversationDto
    @POST("conversations") suspend fun startConversation(@Body body:StartConversationDto): ConversationDto
    @GET("conversations/{id}/messages") suspend fun privateMessages(@Path("id") id:String,@Query("before") before:Long?=null): MessagePageDto
    @POST("conversations/{id}/messages") suspend fun sendPrivateMessage(@Path("id") id:String,@Body body:SendMessageDto): PrivateMessageDto
    @PUT("conversations/{id}/read") suspend fun readConversation(@Path("id") id:String,@Body body:ReadConversationDto)

    @GET("listings/map") suspend fun listingMap(@QueryMap query: Map<String,String>): ListingMapPageDto
    @GET("geography/search") suspend fun searchAddress(@Query("q") query: String): List<GeoPlaceDto>
    @POST("geography/position") suspend fun validatePosition(@Body point: GeoCoordinatesDto): GeoPositionDto
    @POST("recommendations/ai") suspend fun aiRecommendations(): RecommendationsDto
    @Multipart @POST("ai/rent-estimate")
    suspend fun estimateRent(@Part("logement") logement: RequestBody, @Part photos: List<MultipartBody.Part>): RentEstimateDto

    @GET("listings") suspend fun listings(@QueryMap query: Map<String,String>): ListingPageDto
    @GET("listings/mine") suspend fun myListings(@QueryMap query: Map<String,String>): ListingPageDto
    @GET("listings/favorites") suspend fun favoriteListings(@QueryMap query: Map<String,String>): ListingPageDto
    @GET("listings/areas") suspend fun listingAreas(): List<ListingAreaDto>
    @GET("listings/{id}") suspend fun listing(@Path("id") id: String): ListingDto
    @POST("listings") suspend fun createListing(@Body body: ListingWriteDto): ListingDto
    @PUT("listings/{id}") suspend fun updateListing(@Path("id") id: String,@Body body: ListingWriteDto): ListingDto
    @POST("listings/{id}/publish") suspend fun publishListing(@Path("id") id: String): ListingDto
    @POST("listings/{id}/renew") suspend fun renewListing(@Path("id") id: String): ListingDto
    @DELETE("listings/{id}") suspend fun deleteListing(@Path("id") id: String)
    @PUT("listings/{id}/favorite") suspend fun favoriteListing(@Path("id") id: String)
    @DELETE("listings/{id}/favorite") suspend fun unfavoriteListing(@Path("id") id: String)
    @Multipart @POST("listings/{id}/photos") suspend fun uploadListingPhoto(@Path("id") id: String,@Part file: MultipartBody.Part,@Part("caption") caption: RequestBody): ListingDto
    @DELETE("listings/{id}/photos/{photo}") suspend fun deleteListingPhoto(@Path("id") id: String,@Path("photo") photo: String)
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): Session
    @POST("auth/signup?client=android") suspend fun signup(@Body body: SignupRequest)
    @POST("auth/refresh") suspend fun refresh(@Body body: RefreshRequest): Session
    @GET("auth/session") suspend fun session(): IdentitySession
    @POST("auth/logout") suspend fun logout()
    @POST("auth/forgot-password?client=android") suspend fun recover(@Body body: EmailRequest)
    @POST("auth/resend-confirmation?client=android") suspend fun resend(@Body body: EmailRequest)
    @PUT("auth/password") suspend fun password(@Body body: PasswordRequest)
    @GET("auth/google?client=android") suspend fun google(@Query("codeChallenge") challenge: String, @Query("state") state: String): GoogleUrl
    @POST("auth/exchange") suspend fun exchange(@Body body: ExchangeRequest): Session
    @POST("auth/verify") suspend fun verify(@Body body: VerifyRequest): Session
    @POST("users/sync") suspend fun sync(@Body body: Map<String, String> = emptyMap()): ProfileDto
    @GET("users/me/details") suspend fun details(): DetailsDto
    @PUT("onboarding/steps/1") suspend fun identity(@Body body: IdentityDto): DetailsDto
    @PUT("onboarding/steps/2") suspend fun geography(@Body body: GeographyDto): DetailsDto
    @PUT("onboarding/steps/3") suspend fun personal(@Body body: PersonalDto): DetailsDto
    @PUT("onboarding/steps/4") suspend fun contacts(@Body body: ContactsDto): DetailsDto
    @PUT("onboarding/steps/5") suspend fun photos(@Body body: PhotoIds): DetailsDto
    @POST("onboarding/complete") suspend fun complete(): DetailsDto
    @PUT("users/me/preferences") suspend fun preferences(@Body body: PreferencesDto): DetailsDto
    @GET("geography/countries") suspend fun countries(): List<CountryDto>
    @GET("geography/governorates") suspend fun governorates(@Query("countryCode") country: String): List<PlaceDto>
    @GET("geography/localities") suspend fun localities(@Query("governorateId") id: Long): List<PlaceDto>
    @Multipart @POST("profile-photos") suspend fun upload(@Part file: MultipartBody.Part, @Part("caption") caption: RequestBody): Map<String, Any>
    @GET("profile-photos/{id}/signed-url") suspend fun signed(@Path("id") id: String): SignedPhoto
    @PATCH("profile-photos/{id}") suspend fun caption(@Path("id") id: String, @Body body: CaptionRequest)
    @DELETE("profile-photos/{id}") suspend fun deletePhoto(@Path("id") id: String)
}
