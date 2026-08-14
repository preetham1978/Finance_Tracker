package com.example.data.api

import com.example.data.Transaction
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface CloudSyncApiService {
    @PUT("users/{userId}/transactions.json")
    suspend fun uploadTransactions(
        @Path("userId") userId: String,
        @Body transactions: List<Transaction>
    ): Response<Void>

    @GET("users/{userId}/transactions.json")
    suspend fun downloadTransactions(
        @Path("userId") userId: String
    ): Response<List<Transaction>?>
}

object CloudSyncClient {
    private const val BASE_URL = "https://vantage-finance-db-default-rtdb.asia-southeast1.firebasedatabase.app/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: CloudSyncApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudSyncApiService::class.java)
    }
}
