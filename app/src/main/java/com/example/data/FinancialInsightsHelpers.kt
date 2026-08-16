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

/**
 * Bill due-date reminders. Deliberately simple for this pass: checked once
 * whenever the app is opened (via a LaunchedEffect in DashboardScreen)
 * rather than via a true background scheduler (AlarmManager/WorkManager),
 * which would need extra permissions/dependencies and boot-recovery
 * plumbing. This still catches the common case — most people open a
 * finance app at least daily — and needs only the standard
 * POST_NOTIFICATIONS runtime permission (Android 13+), nothing restricted.
 */
object BillReminderChecker {
    private const val PREFS = "vantage_prefs"
    private const val CHANNEL_ID = "bill_reminders"
    private val monthKeyFormat = java.text.SimpleDateFormat("yyyyMM", java.util.Locale.US)

    /**
     * Finds recurring expenses due within the next 2 days that haven't
     * already been reminded about this month, and posts one local
     * notification per match. Silently no-ops if the user hasn't opted in
     * (reminders_enabled) or hasn't granted notification permission —
     * NotificationManagerCompat.notify() is a safe no-op in that case.
     */
    fun checkAndNotify(context: android.content.Context, transactions: List<Transaction>) {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("bill_reminders_enabled", false)) return

        val now = java.util.Calendar.getInstance()
        val today = now.get(java.util.Calendar.DAY_OF_MONTH)
        val monthKey = monthKeyFormat.format(now.time)

        val due = transactions.filter { txn ->
            txn.isRecurring && txn.type == "EXPENSE" && txn.scheduledDayOfMonth != null &&
                (txn.scheduledDayOfMonth - today) in 0..2
        }
        if (due.isEmpty()) return

        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        due.forEach { txn ->
            val dedupKey = "reminded_${txn.id}_$monthKey"
            if (prefs.getBoolean(dedupKey, false)) return@forEach

            val daysUntil = txn.scheduledDayOfMonth!! - today
            val whenText = when (daysUntil) {
                0 -> "today"
                1 -> "tomorrow"
                else -> "in $daysUntil days"
            }
            val symbol = when (txn.currency) {
                "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; "JPY" -> "¥"; else -> "₹"
            }

            val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${txn.title} due $whenText")
                .setContentText("$symbol${String.format("%,.2f", txn.amount)} — scheduled for the ${txn.scheduledDayOfMonth} of the month.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            try {
                notificationManager.notify(txn.id, notification)
            } catch (_: SecurityException) {
                // Permission not granted — nothing we can do here; the UI
                // surfaces a "grant permission" prompt separately.
            }
            prefs.edit().putBoolean(dedupKey, true).apply()
        }
    }
}
