package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holdings")
data class Holding(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    // "MUTUAL_FUND", "CRYPTO", "STOCK", "GOLD", "OTHER"
    val assetType: String = "OTHER",
    // AMFI scheme code for MUTUAL_FUND, CoinGecko coin id for CRYPTO.
    // Null/unused for manually-priced types (STOCK, GOLD, OTHER).
    val identifier: String? = null,
    val quantity: Double = 0.0,
    val avgBuyPrice: Double = 0.0, // per unit, in `currency`
    val currency: String = "INR",
    // User-entered current price — the only price source for STOCK/GOLD/OTHER,
    // and the fallback for MUTUAL_FUND/CRYPTO if a live fetch hasn't run yet.
    val manualCurrentPrice: Double = 0.0,
    // Cached result of the last live price fetch (MUTUAL_FUND/CRYPTO only).
    val lastFetchedPrice: Double = 0.0,
    val lastFetchedTimestamp: Long = 0L,
    val notes: String = ""
)
