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
 * Groq (groq.com) as a fallback text-generation provider for the two AI
 * features that produce personalized financial writing --
 * GeminiManager.getSpendInsights() and getTaxSavingInsights() -- used when
 * the Gemini call itself fails. See GeminiApiService.kt for why the scanner
 * moved to on-device OCR instead of another cloud fallback -- these two
 * features are text-only with no on-device equivalent, so a cloud fallback
 * is still the right call here.
 *
 * Like Gemini, this now talks to OUR backend proxy (functions/index.js's
 * groqChatCompletion Cloud Function) instead of Groq's API directly. The
 * real Groq key lives server-side only (a Firebase Functions v2 secret);
 * the client authenticates with its Firebase Auth ID token instead, which
 * the function verifies and uses to rate-limit per user (protecting the
 * whole app's shared free-tier Groq quota from any single user) before
 * forwarding the same chat-completions payload and passing the response
 * straight back.
 */
interface GroqApiService {
    @POST("groqChatCompletion")
    suspend fun chatCompletion(
        @Header("Authorization") bearerIdToken: String,
        @Body request: GroqChatRequest
    ): GroqChatResponse
}

// Groq's recommended free-tier model for summarization/reasoning-heavy text
// generation as of 2026 (1,000 requests/day, 100k tokens/day on the free
// tier -- the backend's per-user daily rate limit exists specifically to
// keep that shared daily budget from being exhausted by one user). If Groq
// retires this model, check https://console.groq.com/docs/models for the
// current free-tier text model list and update it in functions/index.js
// (the model id also lives there now, not just here).
const val GROQ_DEFAULT_TEXT_MODEL = "llama-3.3-70b-versatile"

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqChatResponse(
    val choices: List<GroqChoice>?
)

data class GroqChoice(
    val message: GroqResponseMessage?
)

data class GroqResponseMessage(
    val content: String?
)

object GroqRetrofitClient {
    // Same Cloud Functions base URL as RetrofitClient (GeminiApiService.kt)
    // -- both proxies are deployed together from the functions/ directory.
    private val BASE_URL = BuildConfig.BACKEND_BASE_URL

    // BASIC (not BODY) logging deliberately -- BODY-level logging on the
    // Gemini/OpenRouter clients previously dumped the Authorization header
    // and full request payload into Logcat, which leaked a live API key
    // when those logs were shared for debugging. Method/URL/status is
    // enough for troubleshooting without repeating that mistake -- doubly
    // true now that the header is a Firebase ID token instead of a Groq key.
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }
}
