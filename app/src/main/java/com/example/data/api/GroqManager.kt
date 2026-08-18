package com.example.data.api

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around GroqApiService for text-only prompts, mirroring the
 * shape of the old OpenRouterManager so GeminiManager can call this as a
 * same-purpose fallback for getSpendInsights()/getTaxSavingInsights() when
 * the Gemini call itself fails. See GroqApiService.kt for why this
 * provider was added, and for why it's now called through our backend
 * proxy with a Firebase ID token rather than an embedded Groq API key.
 */
object GroqManager {
    private const val TAG = "GroqManager"

    fun isApiKeyAvailable(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    suspend fun generateText(systemPrompt: String, userPrompt: String): String? =
        withContext(Dispatchers.IO) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Log.d(TAG, "Groq fallback skipped: not signed in, so the backend proxy has no user to authenticate/rate-limit")
                return@withContext null
            }
            try {
                val token = user.getIdToken(false).await().token
                    ?: return@withContext null
                val request = GroqChatRequest(
                    model = GROQ_DEFAULT_TEXT_MODEL,
                    messages = listOf(
                        GroqMessage(role = "system", content = systemPrompt),
                        GroqMessage(role = "user", content = userPrompt)
                    )
                )
                val response = GroqRetrofitClient.service.chatCompletion(
                    "Bearer $token",
                    request
                )
                response.choices?.firstOrNull()?.message?.content
            } catch (e: Exception) {
                Log.e(TAG, "Error generating text via Groq", e)
                null
            }
        }
}
