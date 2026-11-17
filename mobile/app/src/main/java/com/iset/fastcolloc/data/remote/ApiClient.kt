package com.iset.fastcolloc.data.remote

import com.iset.fastcolloc.BuildConfig
import com.iset.fastcolloc.data.SessionStore
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    fun create(sessions: SessionStore, baseUrl: String = BuildConfig.API_BASE_URL): FastColocApi {
        require(BuildConfig.DEBUG || baseUrl.startsWith("https://")) { "HTTPS obligatoire" }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath.substringAfter("/api/v1/")
                val private = !path.startsWith("auth/") || path in setOf("auth/session", "auth/logout", "auth/password")
                val token = sessions.session()?.accessToken
                val outgoing = if (private && token != null) request.newBuilder().header("Authorization", "Bearer $token").build() else request
                // No payload/header logging: credentials and signed URLs stay private.
                chain.proceed(outgoing)
            }.build()
        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(FastColocApi::class.java)
    }
}
