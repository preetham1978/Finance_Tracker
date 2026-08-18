package com.example.data.api

import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Talks to OUR backend proxy (functions/index.js's geminiGenerateContent
 * Cloud Function), not Google's Gemini API directly.
 *
 * Previously this shipped the Gemini API key inside the compiled app
 * (BuildConfig.GEMINI_API_KEY) and sent it as a query param straight to
 * generativelanguage.googleapis.com. Any APK is trivially decompilable, so
 * that key was extractable by anyone who installed the app -- fine for a
 * personal build, not something to ship broadly. The real key now lives
 * only in the Cloud Function's environment (a Firebase Functions v2
 * secret) and is never sent to a device. The client instead sends its
 * Firebase Auth ID token, which the function verifies and uses to
 * rate-limit per user before forwarding the same GenerateContentRequest
 * payload to Gemini and passing the response straight back -- so the
 * request/response shapes here are unchanged from the old direct-to-Gemini
 * client. See README.md's "Backend proxy" section for deploying the
 * function and functions/index.js for the model id / rate limit.
 */
interface GeminiApiService {
    @POST("geminiGenerateContent")
    suspend fun generateContent(
        @Header("Authorization") bearerIdToken: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    // Base URL of the deployed Cloud Functions, e.g.
    // "https://us-central1-your-project-id.cloudfunctions.net/" -- set via
    // the BACKEND_BASE_URL env var at build time (see .env.example). Must
    // end in a trailing slash for Retrofit's relative @POST paths to
    // resolve correctly.
    private val BASE_URL = BuildConfig.BACKEND_BASE_URL

    private val okHttpClient = OkHttpClient.Builder()
        // BASIC (not BODY) logging deliberately -- BODY-level logging would
        // dump the Authorization header (a live, if short-lived, Firebase
        // ID token) and the full request payload into Logcat. See the
        // matching note on GroqRetrofitClient, which had this exact
        // problem with a previous provider's key.
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}
