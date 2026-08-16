package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.FinanceViewModel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.data.Budget
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import android.content.Intent
import android.provider.Settings
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var category by remember { mutableStateOf("Food") }
    var limit by remember { mutableStateOf("") }
    val categories = listOf("Food", "Shopping", "Bills & Utilities", "Transport", "Entertainment", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.foundation.lazy.LazyRow {
                        items(categories.size) { index ->
                            val cat = categories[index]
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("Monthly Limit") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    limit.toDoubleOrNull()?.let { onConfirm(category, it) }
                },
                enabled = limit.isNotEmpty()
            ) {
                Text("Set Budget")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AIAdvisorTab(viewModel: FinanceViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notifCaptureEnabled by viewModel.notifCaptureEnabled.collectAsState()

    // Re-checked on every recomposition of this key, which we bump when the
    // screen resumes (e.g. returning from the system Notification access
    // settings screen) so the "Granted"/"Not granted" status stays fresh.
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasNotificationAccess = remember(resumeTick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("ai_advisor_tab_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Section
        Text(
            text = "AI Personal Wealth Advisor",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Gemini analyses your spending ledger to surface inefficiencies, suggest savings targets, and guide your wealth growth path.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        // Wealth Diagnostic Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Health Score
            BrutalCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Health Index", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { uiState.healthScore / 100f },
                            modifier = Modifier.size(64.dp),
                            color = when {
                                uiState.healthScore > 75 -> Color(0xFF2E7D32)
                                uiState.healthScore > 50 -> Color(0xFFF9A825)
                                else -> Color(0xFFC62828)
                            },
                            strokeWidth = 6.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Text(
                            text = "${uiState.healthScore}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Runway
            BrutalCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Cash Runway", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Icon(
                        imageVector = Icons.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.runwayDays} Days",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "until zero",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Cash Flow Forecast — projects your liquid balance to end of month
        // using recent burn rate plus any recurring debits still due.
        uiState.cashFlowForecast?.let { forecast ->
            val symbol = FinanceViewModel.currencySymbols[uiState.activeCurrency] ?: "₹"
            BrutalCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (forecast.willGoNegative) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = if (forecast.willGoNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text("Cash Flow Forecast", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(
                        text = if (forecast.daysRemainingInMonth > 0)
                            "Based on your last 30 days of spending, you're projected to have $symbol${String.format("%,.0f", forecast.projectedEndOfMonthBalance)} in ${forecast.daysRemainingInMonth} days (end of month)."
                        else
                            "That's the last day of the month — this projection resets tomorrow.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (forecast.upcomingRecurringTotal > 0) {
                        Text(
                            text = "Includes $symbol${String.format("%,.0f", forecast.upcomingRecurringTotal)} in upcoming scheduled EMIs/recurring debits.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (forecast.willGoNegative) {
                        Text(
                            text = "⚠️ At this rate you may run short before the month ends — consider trimming discretionary spend.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Subscription Watch — recurring merchants detected in your ledger,
        // flagged if they haven't been charged in 45+ days (possible leak
        // still worth cancelling) or just to remind you they exist.
        if (uiState.subscriptions.isNotEmpty()) {
            BrutalCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Subscription Watch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(
                        text = "Recurring merchants detected automatically from your transaction history.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    uiState.subscriptions.take(8).forEach { sub ->
                        val symbol = FinanceViewModel.currencySymbols[sub.currency] ?: "₹"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (sub.isPossibleLeak)
                                        "Not charged in ${sub.daysSinceLastCharge} days — possible leak"
                                    else
                                        "Charged ~monthly · last ${sub.daysSinceLastCharge}d ago",
                                    fontSize = 10.sp,
                                    color = if (sub.isPossibleLeak) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "$symbol${String.format("%,.0f", sub.avgAmount)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Auto-Capture from Notifications — fully hands-off logging: when
        // enabled, bank/UPI transaction notifications are parsed the same
        // way pasted SMS text is, with no manual step at all. Off by
        // default; needs an explicit one-time OS-level grant plus this
        // in-app toggle before anything is read.
        BrutalCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Auto-Capture from Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = "Automatically logs transactions from bank/UPI app notifications — no pasting or sharing needed. Only notifications that look like a transaction alert (amount + debited/credited) are ever read; nothing else is stored or sent anywhere, and you can turn this off anytime.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!hasNotificationAccess) {
                    Text(
                        text = "Notification access: Not granted",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant Notification Access")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Notification access: Granted", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text(
                                text = if (notifCaptureEnabled) "Auto-capture is ON" else "Auto-capture is OFF",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notifCaptureEnabled,
                            onCheckedChange = { viewModel.setNotifCaptureEnabled(it) }
                        )
                    }
                }
            }
        }

        // App Lock + Bill Reminders — grouped since both are quick app-level
        // security/convenience settings rather than AI features.
        val lockEnabled = AppLockManager.isEnabled(context)
        var lockDialogMode by remember { mutableStateOf<AppLockMode?>(null) }
        var billRemindersEnabled by remember(resumeTick) {
            mutableStateOf(context.getSharedPreferences("vantage_prefs", android.content.Context.MODE_PRIVATE).getBoolean("bill_reminders_enabled", false))
        }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                context.getSharedPreferences("vantage_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("bill_reminders_enabled", true).apply()
                billRemindersEnabled = true
            }
        }

        BrutalCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (lockEnabled) Icons.Filled.Lock else Icons.Filled.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("App Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // App Lock row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Lock (PIN)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (lockEnabled) "Vantage locks whenever you leave the app" else "Require a PIN to open the app",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lockEnabled,
                        onCheckedChange = { checked ->
                            lockDialogMode = if (checked) AppLockMode.SETUP else AppLockMode.CONFIRM_TO_DISABLE
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Bill Reminders row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Bill & EMI Reminders", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Notifies you when a recurring bill is due within 2 days (checked each time you open the app)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = billRemindersEnabled,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.getSharedPreferences("vantage_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("bill_reminders_enabled", checked).apply()
                                billRemindersEnabled = checked
                            }
                        }
                    )
                }
            }
        }

        if (lockDialogMode != null) {
            Dialog(
                onDismissRequest = { lockDialogMode = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppLockScreen(
                        mode = lockDialogMode!!,
                        onUnlocked = { lockDialogMode = null },
                        onCancel = { lockDialogMode = null }
                    )
                }
            }
        }

        // Statistics Summary Card
        BrutalCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Budget Management", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                // Add Budget Button
                var showBudgetDialog by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showBudgetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Setup Monthly Budgets")
                }

                if (showBudgetDialog) {
                    BudgetDialog(
                        onDismiss = { showBudgetDialog = false },
                        onConfirm = { category, limit ->
                            viewModel.setBudget(category, limit)
                            showBudgetDialog = false
                        }
                    )
                }

                if (uiState.budgets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    uiState.budgets.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(b.category, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${FinanceViewModel.currencySymbols[uiState.activeCurrency]}${b.monthlyLimit.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { viewModel.deleteBudget(b) }) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Statistics Summary Card (Original)
        BrutalCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Current Monthly Status", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Income Logged", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val symbol = FinanceViewModel.currencySymbols[uiState.activeCurrency] ?: "₹"
                        Text("$symbol${String.format("%,.2f", uiState.totalIncome)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2E7D32))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Expenses Logged", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val symbol = FinanceViewModel.currencySymbols[uiState.activeCurrency] ?: "₹"
                        Text("$symbol${String.format("%,.2f", uiState.totalExpense)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFC62828))
                    }
                }
            }
        }

        // Trigger Button
        Button(
            onClick = { viewModel.generateSpendInsights() },
            enabled = !uiState.isAnalyzingSpend,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isAnalyzingSpend) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyzing ledger with Gemini...")
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate AI Spend Insights")
            }
        }

        // Output Card Display
        if (uiState.spendInsights.isNotEmpty()) {
            BrutalCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Gemini Financial Advisor", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Text(
                        text = parseMarkdown(uiState.spendInsights),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        } else {
            // Placeholder empty state
            BrutalCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("💡", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Insights list is ready!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap the button above to run a real-time spending efficiency assessment with Gemini AI.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
