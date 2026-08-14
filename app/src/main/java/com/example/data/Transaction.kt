package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val amount: Double = 0.0, // Amount in entered currency
    val category: String = "",
    val timestamp: Long = 0L,
    val notes: String = "",
    val type: String = "EXPENSE", // "INCOME" or "EXPENSE"
    val paymentMethod: String = "CASH", // "CASH", "BANK_ACCOUNT", "CREDIT_CARD"
    val creditCardBank: String? = null, // Standard Chartered, Kotak, RBL, etc.
    val isRecurring: Boolean = false, // Recurring Monthly for Loans, etc.
    val recurrenceInterval: String? = null, // "MONTHLY"
    val currency: String = "INR", // Entered currency e.g. "INR", "USD", "EUR", "GBP"
    val scheduledDayOfMonth: Int? = null // Day of month for recurring EMI/Loans
)
