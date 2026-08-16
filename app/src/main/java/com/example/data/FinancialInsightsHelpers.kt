package com.example.data

/**
 * Pure, dependency-free analysis helpers used by FinanceViewModel to power
 * two AI Advisor cards:
 *  - Subscription Watch: flags merchants you're charged roughly monthly so
 *    you can spot subscriptions you forgot you're paying for.
 *  - Cash Flow Forecast: projects your liquid balance to the end of the
 *    current month using your recent burn rate plus any recurring debits
 *    still due.
 *
 * Both operate purely on the local transaction ledger — no network calls,
 * no new permissions.
 */

data class SubscriptionAlert(
    val title: String,
    val category: String,
    val avgAmount: Double,
    val currency: String,
    val occurrences: Int,
    val lastChargedTimestamp: Long,
    val daysSinceLastCharge: Int,
    val isPossibleLeak: Boolean // hasn't been charged in 45+ days but still looks "active"
)

object SubscriptionDetector {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Groups expenses by (normalized) title and flags groups that repeat on
     * a roughly-monthly cadence (2+ charges, ~20-40 days apart) or are
     * explicitly marked recurring. "Personal Loan" is excluded — that's debt
     * repayment, not a subscription someone might want to cancel.
     */
    fun detect(transactions: List<Transaction>, nowMillis: Long): List<SubscriptionAlert> {
        return transactions
            .filter { it.type == "EXPENSE" && it.category != "Personal Loan" && it.title.isNotBlank() }
            .groupBy { it.title.trim().lowercase() }
            .mapNotNull { (_, txs) ->
                if (txs.size < 2 && txs.none { it.isRecurring }) return@mapNotNull null
                val sorted = txs.sortedBy { it.timestamp }
                val gaps = sorted.zipWithNext { a, b -> (b.timestamp - a.timestamp) / DAY_MS }
                val looksMonthly = gaps.isNotEmpty() && gaps.all { it in 18..45 }
                val looksRecurringFlag = sorted.any { it.isRecurring }
                if (!looksMonthly && !looksRecurringFlag) return@mapNotNull null

                val last = sorted.last()
                val daysSince = ((nowMillis - last.timestamp) / DAY_MS).toInt().coerceAtLeast(0)

                SubscriptionAlert(
                    title = last.title,
                    category = last.category,
                    avgAmount = sorted.map { it.amount }.average(),
                    currency = last.currency,
                    occurrences = sorted.size,
                    lastChargedTimestamp = last.timestamp,
                    daysSinceLastCharge = daysSince,
                    isPossibleLeak = daysSince >= 45
                )
            }
            .sortedByDescending { it.daysSinceLastCharge }
    }
}

data class CashFlowForecast(
    val currentBalance: Double,
    val projectedEndOfMonthBalance: Double,
    val avgDailyNet: Double,
    val daysRemainingInMonth: Int,
    val upcomingRecurringTotal: Double,
    val willGoNegative: Boolean
)

object CashFlowForecaster {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * @param convert converts an amount between currencies (from -> to),
     *   passed in so this stays free of any ViewModel/companion coupling.
     */
    fun forecast(
        transactions: List<Transaction>,
        currentBalance: Double,
        activeCurrency: String,
        nowMillis: Long,
        convert: (amount: Double, from: String, to: String) -> Double
    ): CashFlowForecast {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val today = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val daysRemaining = (daysInMonth - today).coerceAtLeast(0)

        val thirtyDaysAgo = nowMillis - 30L * DAY_MS
        val recent = transactions.filter {
            it.timestamp in thirtyDaysAgo..nowMillis && it.paymentMethod != "CREDIT_CARD"
        }
        val recentIncome = recent.filter { it.type == "INCOME" }
            .sumOf { convert(it.amount, it.currency, activeCurrency) }
        val recentExpense = recent.filter { it.type == "EXPENSE" }
            .sumOf { convert(it.amount, it.currency, activeCurrency) }
        val avgDailyNet = (recentIncome - recentExpense) / 30.0

        // Recurring debits (EMIs etc.) still scheduled to hit later this month
        val upcoming = transactions
            .filter { it.isRecurring && it.type == "EXPENSE" && (it.scheduledDayOfMonth ?: -1) > today }
            .sumOf { convert(it.amount, it.currency, activeCurrency) }

        val projected = currentBalance + (avgDailyNet * daysRemaining) - upcoming

        return CashFlowForecast(
            currentBalance = currentBalance,
            projectedEndOfMonthBalance = projected,
            avgDailyNet = avgDailyNet,
            daysRemainingInMonth = daysRemaining,
            upcomingRecurringTotal = upcoming,
            willGoNegative = projected < 0
        )
    }
}
