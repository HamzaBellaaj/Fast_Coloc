package com.iset.fastcolloc

import com.iset.fastcolloc.data.remote.FastColocApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RentEstimateMultipartTest {
    @Test fun allSelectedPhotosTravelInOneEstimateRequest() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"success":true,"estimation":{"disponible":true,"loyer_conseille_tnd":1250}}
            """.trimIndent()))
            val api = Retrofit.Builder().baseUrl(server.url("/api/v1/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(FastColocApi::class.java)
            val photos = listOf("salon-photo", "cuisine-photo", "chambre-photo").mapIndexed { index, bytes ->
                MultipartBody.Part.createFormData("photo", "photo-$index.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            }
            val result = api.estimateRent("""{"localisation":"Ariana","type":"S+2"}""".toRequestBody("application/json".toMediaType()), photos)
            assertTrue(result.success)
            val request = server.takeRequest()
            assertEquals("/api/v1/ai/rent-estimate", request.path)
            val body = request.body.readUtf8()
            assertEquals(3, Regex("name=\"photo\"").findAll(body).count())
            for (photo in listOf("salon-photo", "cuisine-photo", "chambre-photo")) assertTrue(body.contains(photo))
            assertTrue(body.contains("name=\"logement\""))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
