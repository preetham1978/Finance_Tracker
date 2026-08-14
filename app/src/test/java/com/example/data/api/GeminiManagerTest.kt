package com.example.data.api

import com.example.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeminiManagerTest {
    @Test
    fun testApiKeyAvailability() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val isAvailable = GeminiManager.isApiKeyAvailable()
        assertTrue("API Key should be available: '\$apiKey'", isAvailable)
    }

    @Test
    fun analyzeBillText_emptyText_shouldReturnUnknown() = runBlocking {
        val result = GeminiManager.analyzeBillText("")
        
        assertNotNull(result)
        assertEquals("Unknown Transaction", result?.title)
        assertEquals(0.0, result?.amount ?: -1.0, 0.0)
    }

    @Test
    fun analyzeBill_emptyBase64_shouldReturnUnknown() = runBlocking {
        val result = GeminiManager.analyzeBill("", "test.jpg")
        
        assertNotNull(result)
        assertEquals("Unknown Transaction", result?.title)
        assertEquals(0.0, result?.amount ?: -1.0, 0.0)
    }
}
