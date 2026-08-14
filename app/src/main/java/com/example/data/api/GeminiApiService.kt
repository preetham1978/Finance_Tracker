package com.example.data.api

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.FinanceApplication
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.1-flash-lite-preview:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val TAG = "RetrofitClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    /**
     * The Gemini API key ships inside the compiled app (BuildConfig.GEMINI_API_KEY), so anyone
     * can extract it from the APK. As a mitigation, restrict the key in Google Cloud Console to
     * "Android apps" (package name + SHA-1 signing certificate) and send the matching identity
     * headers Google checks the restriction against on every request. This isn't bulletproof -
     * a determined attacker can pull the cert fingerprint from the APK too and spoof these
     * headers - but it stops casual key scraping/bots with no backend required. See the repo
     * README for the Cloud Console steps to actually enable the restriction; without that step
     * these headers are sent but not enforced.
     */
    private fun signingCertSha1(): String? {
        return try {
            val context = FinanceApplication.appContext
            @Suppress("DEPRECATION")
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.firstOrNull()
            } ?: return null

            val digest = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
            // Google expects uppercase hex with no colons for the X-Android-Cert header.
            digest.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not compute signing certificate fingerprint", e)
            null
        }
    }

    private val androidIdentityInterceptor = Interceptor { chain ->
        val context = FinanceApplication.appContext
        val requestBuilder = chain.request().newBuilder()
            .addHeader("X-Android-Package", context.packageName)
        signingCertSha1()?.let { sha1 -> requestBuilder.addHeader("X-Android-Cert", sha1) }
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(androidIdentityInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
