package com.example.data.api

import org.junit.Assert.assertFalse
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
        // Cloud AI now goes through a backend proxy authenticated by the
        // signed-in user's Firebase ID token (see GeminiManager.kt /
        // functions/index.js), instead of a BuildConfig-embedded API key.
        // isApiKeyAvailable() is really "is cloud AI reachable", which is
        // now equivalent to "is someone signed in" -- Robolectric starts
        // with no signed-in FirebaseAuth user (same baseline other tests
        // in this suite rely on, e.g. EditProfileDialogTest), so this
        // should be false here.
        val isAvailable = GeminiManager.isApiKeyAvailable()
        assertFalse("Cloud AI should not be available with no signed-in user", isAvailable)
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
