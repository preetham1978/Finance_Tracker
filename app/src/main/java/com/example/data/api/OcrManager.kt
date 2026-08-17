package com.example.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device OCR fallback for the bill scanner. Runs entirely on-device via
 * ML Kit's Text Recognition -- no network call, no API key, no billing --
 * added after Gemini's prepay billing kept hitting zero and OpenRouter's
 * free vision tier proved too unreliable (multi-minute "Upstream idle
 * timeout" failures, see GeminiManager.analyzeBill() history). Unlike a
 * second cloud provider, this can never be rate-limited, timed out, or
 * billing-gated, so it guarantees the scanner extracts *something* useful
 * even when every cloud AI provider is unavailable.
 */
object OcrManager {
    private const val TAG = "OcrManager"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Runs on-device OCR against the same base64 JPEG payload used for the cloud calls. */
    suspend fun recognizeTextFromBase64(billBase64: String): String? {
        val bitmap = try {
            val bytes = Base64.decode(billBase64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Could not decode receipt image for on-device OCR", e)
            null
        } ?: return null

        return recognizeText(bitmap)
    }

    private suspend fun recognizeText(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "On-device OCR failed", e)
                    if (cont.isActive) cont.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting on-device OCR", e)
            if (cont.isActive) cont.resume(null)
        }
    }

    /**
     * Turns raw OCR'd receipt text into a ParsedBill using simple heuristics --
     * no LLM involved. Receipts differ from the bank-SMS text
     * GeminiManager.getMockTextAnalysis() was tuned for (a masked SMS line vs.
     * a multi-line printed receipt/GPay screenshot), so this uses
     * receipt-shaped rules instead:
     *  - Title: first substantial text line, since the store/merchant name is
     *    almost always the header on a printed receipt or payment screenshot.
     *  - Amount: prefers a number next to "total"/"grand total"/"amount
     *    paid"/"net payable"/"amount due"; falls back to the largest number
     *    found, since the total is nearly always the biggest figure on a
     *    receipt.
     *  - Category: reuses GeminiManager's existing offline keyword-based
     *    category matcher against the merchant name plus the full OCR text.
     */
    fun parseReceiptText(rawText: String, billFileName: String): ParsedBill {
        if (rawText.isBlank()) {
            return ParsedBill(
                title = "Unknown Transaction",
                amount = 0.0,
                category = "Other",
                currency = "INR",
                notes = "On-device OCR could not read any text from this image."
            )
        }

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val title = lines.firstOrNull { line ->
            line.length in 2..40 && line.any { it.isLetter() } &&
                line.count { it.isDigit() } < line.length / 2
        } ?: "Unknown Transaction"

        val totalKeywordRegex = Regex(
            """(?i)(grand\s*total|total\s*amount|amount\s*paid|net\s*payable|total\s*due|amount\s*due|total)\D{0,10}(\d[\d,]*\.?\d*)"""
        )
        val numberRegex = Regex("""\d{1,3}(?:[,.]\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?""")

        val amount = totalKeywordRegex.find(rawText)?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull()
            ?: numberRegex.findAll(rawText)
                .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
                .maxOrNull()
            ?: 0.0

        val currency = when {
            rawText.contains("$") -> "USD"
            rawText.contains("€") -> "EUR"
            rawText.contains("£") -> "GBP"
            else -> "INR"
        }

        val category = GeminiManager.getOfflineCategorySuggestion("$title $rawText")

        return ParsedBill(
            title = title,
            amount = amount,
            category = category,
            currency = currency,
            notes = "Extracted on-device via OCR from $billFileName (no AI billing required)."
        )
    }
}
