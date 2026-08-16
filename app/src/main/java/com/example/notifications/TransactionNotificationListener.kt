package com.example.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.FinanceApplication
import com.example.data.Transaction
import com.example.data.api.GeminiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional, opt-in auto-capture: reads notifications (title + text only,
 * never SMS or any other app data) and, when one looks like a bank/UPI
 * transaction alert, auto-creates a transaction using the same Gemini
 * text-parsing pipeline the Paste-Text quick-add uses.
 *
 * Two independent gates before anything is processed:
 *  1. OS-level "Notification access" — the user must explicitly grant this
 *     in Settings; Android does not allow requesting it via a normal
 *     runtime-permission popup.
 *  2. App-level toggle (FinanceViewModel.notifCaptureEnabled, persisted in
 *     vantage_prefs, default OFF) — lets the user disable processing at any
 *     time without revoking OS access, and means simply installing the app
 *     never turns this on silently.
 *
 * Notifications are filtered hard before anything is parsed or stored:
 * skipped unless the text contains both a currency/amount pattern AND a
 * transaction keyword (debited/credited/spent/paid/etc.), and always
 * skipped if it looks like an OTP/verification message. Non-matching
 * notifications are never read further, logged, or sent anywhere.
 */
class TransactionNotificationListener : NotificationListenerService() {

    private val TAG = "TxnNotifListener"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private val AMOUNT_PATTERN = Regex("(?i)(?:rs\\.?|inr|₹|\\$|€|£)\\s?\\d[\\d,]*(?:\\.\\d{1,2})?")
        private val TXN_KEYWORDS = listOf(
            "debited", "credited", "spent", "paid", "payment of", "withdrawn",
            "purchase of", "txn of", "transaction of"
        )
        private val EXCLUDE_KEYWORDS = listOf(
            "otp", "one time password", "verification code", "do not share"
        )
        private const val MAX_PROCESSED_HISTORY = 60
    }

    // In-memory ring buffer of recently processed notification signatures,
    // to avoid double-inserting when a bank app updates/reposts the same
    // notification (common right after a transaction fires).
    private val processedSignatures = ArrayDeque<String>()

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val notification = sbn ?: return

        // Never process our own notifications.
        if (notification.packageName == applicationContext.packageName) return

        val app = applicationContext as? FinanceApplication ?: return
        val prefs = app.getSharedPreferences("vantage_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notif_capture_enabled", false)) return

        val extras = notification.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = (extras.getCharSequence("android.bigText") ?: extras.getCharSequence("android.text"))
            ?.toString().orEmpty()
        val combined = "$title. $text".trim()
        if (combined.isBlank()) return

        val lower = combined.lowercase()
        if (EXCLUDE_KEYWORDS.any { lower.contains(it) }) return
        if (!AMOUNT_PATTERN.containsMatchIn(combined)) return
        if (TXN_KEYWORDS.none { lower.contains(it) }) return

        val signature = "${notification.packageName}|$combined".take(200)
        if (processedSignatures.contains(signature)) return
        processedSignatures.addLast(signature)
        while (processedSignatures.size > MAX_PROCESSED_HISTORY) processedSignatures.removeFirst()

        serviceScope.launch {
            try {
                val parsed = GeminiManager.analyzeBillText(combined) ?: return@launch
                if (parsed.amount <= 0.0) return@launch

                val type = if (lower.contains("credited") && !lower.contains("debited")) "INCOME" else "EXPENSE"

                app.repository.insert(
                    Transaction(
                        title = parsed.title,
                        amount = parsed.amount,
                        category = if (type == "INCOME") "Other Income" else parsed.category,
                        timestamp = System.currentTimeMillis(),
                        notes = "Auto-captured from a ${notification.packageName} notification. ${parsed.notes}".trim(),
                        type = type,
                        paymentMethod = "BANK_ACCOUNT",
                        currency = parsed.currency
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-capture notification transaction", e)
            }
        }
    }
}
