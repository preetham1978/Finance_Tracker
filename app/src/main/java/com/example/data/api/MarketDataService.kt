package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Free, no-API-key price sources for the investment portfolio tracker.
 * Deliberately limited to what's reliably free:
 *  - Mutual funds: AMFI's official daily NAV feed (every Indian scheme).
 *  - Crypto: CoinGecko's public simple-price endpoint.
 * Stocks and gold have no equivalent free/reliable live-price source, so
 * those stay manually priced in the Holding entity — see FinanceViewModel.
 */
object MarketDataService {
    private const val TAG = "MarketDataService"
    private val client = OkHttpClient()

    /**
     * Fetches today's NAV for an Indian mutual fund scheme from AMFI's
     * public NAVAll.txt feed (~a few hundred KB, one line per scheme,
     * semicolon-delimited: "Scheme Code;ISIN Div Payout;ISIN Growth;
     * Scheme Name;Net Asset Value;Date"). Parsed fresh on every call —
     * this is only invoked on an explicit user refresh, not polled.
     */
    suspend fun fetchMutualFundNav(schemeCode: String): Double? = withContext(Dispatchers.IO) {
        if (schemeCode.isBlank()) return@withContext null
        try {
            val request = Request.Builder()
                .url("https://www.amfiindia.com/spider/webpages/NAVAll.txt")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                for (line in body.lineSequence()) {
                    val parts = line.split(";")
                    if (parts.size >= 5 && parts[0].trim() == schemeCode.trim()) {
                        return@withContext parts[4].trim().toDoubleOrNull()
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch MF NAV for scheme $schemeCode", e)
            null
        }
    }

    /**
     * Fetches the current price for a crypto asset from CoinGecko's free
     * public endpoint. `coinId` is CoinGecko's own id (e.g. "bitcoin",
     * "ethereum" — not the ticker symbol). `vsCurrency` is a normal
     * 3-letter code (INR, USD, ...).
     */
    suspend fun fetchCryptoPrice(coinId: String, vsCurrency: String): Double? = withContext(Dispatchers.IO) {
        if (coinId.isBlank()) return@withContext null
        try {
            val currency = vsCurrency.lowercase()
            val request = Request.Builder()
                .url("https://api.coingecko.com/api/v3/simple/price?ids=$coinId&vs_currencies=$currency")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val coinObj = if (json.has(coinId)) json.getAsJsonObject(coinId) else null
                val priceElement = coinObj?.get(currency)
                priceElement?.asDouble
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch crypto price for $coinId", e)
            null
        }
    }
}
