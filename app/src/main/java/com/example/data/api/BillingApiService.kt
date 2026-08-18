package com.example.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Talks to functions/index.js's verifyPlayPurchase Cloud Function -- the
 * server-side step that turns a Google Play purchase into an actual
 * subscription tier. See BillingManager.kt for where this gets called
 * (right after the Play Billing Library reports a successful purchase)
 * and verifyPlayPurchase's own doc comment for why this has to happen on
 * a server instead of the client just writing its own tier to Firestore.
 */
interface BillingApiService {
    @POST("verifyPlayPurchase")
    suspend fun verifyPurchase(
        @Header("Authorization") bearerIdToken: String,
        @Body request: VerifyPurchaseRequest
    ): VerifyPurchaseResponse
}

data class VerifyPurchaseRequest(
    val productId: String,
    val purchaseToken: String
)

data class VerifyPurchaseResponse(
    val tier: String,
    val subscriptionState: String?
)

object BillingRetrofitClient {
    // Same Cloud Functions base URL as RetrofitClient (GeminiApiService.kt)
    // and GroqRetrofitClient (GroqApiService.kt) -- all three proxies are
    // deployed together from the functions/ directory.
    private val BASE_URL = com.example.BuildConfig.BACKEND_BASE_URL

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: BillingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BillingApiService::class.java)
    }
}
