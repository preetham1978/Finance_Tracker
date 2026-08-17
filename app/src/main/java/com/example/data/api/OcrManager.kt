package com.example.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine

object OcrManager {
    private const val TAG = "OcrManager"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

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
                    if (cont.isActive) cont.resumeWith(Result.success(visionText.text))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "On-device OCR failed", e)
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting on-device OCR", e)
            if (cont.isActive) cont.resumeWith(Result.success(null))
        }
    }

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