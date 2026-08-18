package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun getBase64FromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Scale down the bitmap if it's too large. 1600px and JPEG
            // quality 92 (up from the previous 1024px/80) -- receipts often
            // have small printed totals/line-items, and the previous, more
            // aggressive downscale was very likely throwing away enough
            // detail to cause Gemini/on-device OCR misreads on those. Both
            // Gemini's per-request cost and typical receipt photo sizes are
            // small enough that this bump is negligible either way.
            val maxDimension = 1600
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDimension || height > maxDimension) {
                maxDimension.toFloat() / Math.max(width, height)
            } else {
                1.0f
            }

            val scaledWidth = Math.round(scale * width)
            val scaledHeight = Math.round(scale * height)
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
            val bytes = outputStream.toByteArray()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
