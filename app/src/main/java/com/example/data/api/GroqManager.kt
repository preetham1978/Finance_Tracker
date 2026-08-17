package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around GroqApiService for text-only prompts, mirroring the
 * shape of the old OpenRouterManager so GeminiManager can call this as a
 * same-purpose fallback for getSpendInsights()/getTaxSavingInsights() when
 * the Gemini call itself fails. See GroqApiService.kt for why this
 * provider was added.
 */
object GroqManager {
    private const val TAG = "GroqManager"

    fun isApiKeyAvailable(): Boolean {
        return BuildConfig.GROQ_API_KEY.isNotEmpty() && BuildConfig.GROQ_API_KEY != "PLACEHOLDER_KEY"
    }

    suspend fun generateText(systemPrompt: String, userPrompt: String): String? =
        withContext(Dispatchers.IO) {
            if (!isApiKeyAvailable()) {
                Log.d(TAG, "Groq fallback skipped: GROQ_API_KEY is not set (empty or placeholder) in this build")
                return@withContext null
            }
            try {
                val request = GroqChatRequest(
                    model = GROQ_DEFAULT_TEXT_MODEL,
                    messages = listOf(
                        GroqMessage(role = "system", content = systemPrompt),
                        GroqMessage(role = "user", content = userPrompt)
                    )
                )
                val response = GroqRetrofitClient.service.chatCompletion(
                    "Bearer ${BuildConfig.GROQ_API_KEY}",
                    request
                )
                response.choices?.firstOrNull()?.message?.content
            } catch (e: Exception) {
                Log.e(TAG, "Error generating text via Groq", e)
                null
            }
        }
}
