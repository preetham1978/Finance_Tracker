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
 * Groq (groq.com) as a fallback text-generation provider for the two AI
 * features that produce personalized financial writing --
 * GeminiManager.getSpendInsights() and getTaxSavingInsights() -- used when
 * the Gemini call itself fails (e.g. the prepay-billing-depletion issue
 * documented in GeminiApiService.kt). Groq runs open-weight models on its
 * own hardware rather than proxying to shared third-party capacity, so its
 * free tier has a real reputation for being fast and stable, unlike
 * OpenRouter's free *vision* routing which kept timing out (see
 * OcrManager.kt for why the *scanner* moved to on-device OCR instead of
 * another cloud fallback -- these two features are text-only with no
 * on-device equivalent, so a cloud fallback is still the right call here).
 *
 * Uses Groq's OpenAI-compatible chat completions endpoint. Requires its
 * own free API key from https://console.groq.com/keys, supplied via the
 * GROQ_API_KEY environment variable at build time (same pattern as
 * GEMINI_API_KEY) -- see BuildConfig.GROQ_API_KEY.
 */
interface GroqApiService {
    @POST("openai/v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") bearerToken: String,
        @Body request: GroqChatRequest
    ): GroqChatResponse
}

// Groq's recommended free-tier model for summarization/reasoning-heavy text
// generation as of 2026 (1,000 requests/day, 100k tokens/day on the free
// tier -- plenty for occasional spend/tax insight generation). If Groq
// retires this model, check https://console.groq.com/docs/models for the
// current free-tier text model list.
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
    private const val BASE_URL = "https://api.groq.com/"

    // BASIC (not BODY) logging deliberately -- BODY-level logging on the
    // Gemini/OpenRouter clients previously dumped the Authorization header
    // and full request payload into Logcat, which leaked a live API key
    // when those logs were shared for debugging. Method/URL/status is
    // enough for troubleshooting without repeating that mistake.
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
