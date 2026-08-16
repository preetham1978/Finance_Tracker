package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen balance widget: shows the current liquid balance and a
 * quick "+" shortcut straight into Add Transaction, so logging or checking
 * a balance doesn't require opening the app first.
 *
 * Balance is computed directly from Room (not through FinanceViewModel,
 * which a widget can't hold a reference to) and converted to INR using a
 * small static rate table mirroring FinanceViewModel.convert — a
 * simplification for this pass; multi-currency portfolios will see an
 * INR-normalized total here rather than their chosen active currency.
 *
 * Appearance (background color + opacity) is per-widget-instance, chosen in
 * WidgetConfigActivity when the widget is placed (or reconfigured later via
 * long-press -> Edit on most launchers) and stored in vantage_prefs keyed
 * by widget ID.
 */
class BalanceWidgetProvider : AppWidgetProvider() {

    private data class WidgetTheme(val bg: Int, val text: Int, val subtext: Int)

    companion object {
        private const val PREFS = "vantage_prefs"

        private val RATE_TO_INR = mapOf(
            "INR" to 1.0,
            "USD" to 83.5,
            "EUR" to 90.76,
            "GBP" to 105.7,
            "JPY" to 0.528
        )

        private val THEME_GOLD = WidgetTheme(0xFFFFD60A.toInt(), 0xFF000000.toInt(), 0xB3000000.toInt())
        private val THEME_DARK = WidgetTheme(0xFF1A1A1A.toInt(), 0xFFFFFFFF.toInt(), 0xB3FFFFFF.toInt())
        private val THEME_LIGHT = WidgetTheme(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xB3000000.toInt())

        private fun toInr(amount: Double, currency: String): Double {
            val rate = RATE_TO_INR[currency] ?: 1.0
            return amount * rate
        }

        /**
         * Call after any transaction mutation so the widget refreshes
         * promptly, instead of waiting for the ~30 minute OS periodic
         * update. Safe to call even if no widget is currently placed.
         */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetProvider::class.java))
            if (ids.isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.getDatabase(context).transactionDao()
                val transactions = dao.getAllTransactions().first()

                val income = transactions.filter { it.type == "INCOME" }
                    .sumOf { toInr(it.amount, it.currency) }
                val expense = transactions.filter { it.type == "EXPENSE" }
                    .sumOf { toInr(it.amount, it.currency) }
                val ccSpend = transactions.filter { it.type == "EXPENSE" && it.paymentMethod == "CREDIT_CARD" }
                    .sumOf { toInr(it.amount, it.currency) }
                val balance = income - (expense - ccSpend)

                ids.forEach { id -> updateWidget(context, manager, id, balance) }
            }
        }

        private fun resolveTheme(context: Context, widgetId: Int): WidgetTheme {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val presetKey = prefs.getString("widget_theme_$widgetId", "GOLD") ?: "GOLD"
            return when (presetKey) {
                "DARK" -> THEME_DARK
                "LIGHT" -> THEME_LIGHT
                "AUTO" -> {
                    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightMode == Configuration.UI_MODE_NIGHT_YES) THEME_DARK else THEME_LIGHT
                }
                else -> THEME_GOLD
            }
        }

        private fun applyOpacity(baseColor: Int, opacityPercent: Int): Int {
            val fraction = opacityPercent.coerceIn(0, 100) / 100.0
            val baseAlpha = (baseColor ushr 24) and 0xFF
            val resolvedAlpha = (baseAlpha * fraction).toInt().coerceIn(0, 255)
            return (resolvedAlpha shl 24) or (baseColor and 0x00FFFFFF)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int, balance: Double) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val opacity = prefs.getInt("widget_opacity_$widgetId", 100)
            val theme = resolveTheme(context, widgetId)
            val bgColor = applyOpacity(theme.bg, opacity)

            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
            views.setTextColor(R.id.widget_balance, theme.text)
            views.setTextColor(R.id.widget_title, theme.subtext)
            views.setTextViewText(R.id.widget_balance, "₹" + String.format("%,.0f", balance))

            val addIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("OPEN_ADD_TRANSACTION", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val addPendingIntent = PendingIntent.getActivity(
                context, widgetId, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add_button, addPendingIntent)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, widgetId + 100000, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_balance, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { id ->
            editor.remove("widget_theme_$id")
            editor.remove("widget_opacity_$id")
        }
        editor.apply()
    }
}
