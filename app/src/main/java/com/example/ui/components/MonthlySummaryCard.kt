package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Transaction
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MonthlySummaryCard(
    transactions: List<Transaction>,
    activeCurrency: String,
    modifier: Modifier = Modifier
) {
    val currencySymbol = remember(activeCurrency) {
        FinanceViewModel.currencySymbols[activeCurrency] ?: "₹"
    }

    // Helper to group transactions by Month-Year
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val monthYearKeyFormat = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()) }

    // Aggregate unique months from transactions (newest first)
    val uniqueMonths = remember(transactions) {
        if (transactions.isEmpty()) {
            val current = monthYearKeyFormat.format(Date()) to monthYearFormat.format(Date())
            listOf(current)
        } else {
            transactions
                .map {
                    val date = Date(it.timestamp)
                    monthYearKeyFormat.format(date) to monthYearFormat.format(date)
                }
                .distinctBy { it.first }
                .sortedByDescending { it.first }
        }
    }

    // Selected Month index
    var selectedMonthIndex by remember(uniqueMonths) { mutableIntStateOf(0) }
    
    // Safety check for out of bounds on state modification
    val safeMonthIndex = selectedMonthIndex.coerceIn(0, uniqueMonths.lastIndex)
    val selectedMonth = uniqueMonths[safeMonthIndex]

    // Dropdown toggle
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Filter transactions for selected Month
    val monthlyTransactions = remember(transactions, selectedMonth) {
        transactions.filter {
            val date = Date(it.timestamp)
            monthYearKeyFormat.format(date) == selectedMonth.first
        }
    }

    // Calculate aggregated stats for selected month in Active Currency
    val stats = remember(monthlyTransactions, activeCurrency) {
        var income = 0.0
        var expense = 0.0
        val categoryMap = mutableMapOf<String, Double>()

        monthlyTransactions.forEach { tx ->
            val convertedAmount = FinanceViewModel.convert(tx.amount, tx.currency, activeCurrency)
            if (tx.type == "INCOME") {
                income += convertedAmount
            } else {
                expense += convertedAmount
                categoryMap[tx.category] = (categoryMap[tx.category] ?: 0.0) + convertedAmount
            }
        }

        val topCategories = categoryMap.toList()
            .sortedByDescending { it.second }
            .take(3)

        Triple(income, expense, topCategories)
    }

    val (totalIncome, totalExpense, topCategories) = stats
    val netSavings = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) {
        ((netSavings / totalIncome) * 100).coerceIn(0.0, 100.0)
    } else {
        0.0
    }

    BrutalCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_summary_card"),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Month Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly Ledger Summary",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Track your budget, savings and patterns",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Month Switcher controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (safeMonthIndex < uniqueMonths.lastIndex) {
                                selectedMonthIndex = safeMonthIndex + 1
                            }
                        },
                        enabled = safeMonthIndex < uniqueMonths.lastIndex,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Previous Month",
                            modifier = Modifier.size(20.dp),
                            tint = if (safeMonthIndex < uniqueMonths.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }

                    // Dropdown activator text
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { dropdownExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = selectedMonth.second,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select Month",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            uniqueMonths.forEachIndexed { index, pair ->
                                DropdownMenuItem(
                                    text = { Text(pair.second, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        selectedMonthIndex = index
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (safeMonthIndex > 0) {
                                selectedMonthIndex = safeMonthIndex - 1
                            }
                        },
                        enabled = safeMonthIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Next Month",
                            modifier = Modifier.size(20.dp),
                            tint = if (safeMonthIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // Quick Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Income Mini-Card
                BrutalCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFE8F5E9)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Income",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Monthly Income",
                                fontSize = 10.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format("%s%,.2f", currencySymbol, totalIncome),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Expense Mini-Card
                BrutalCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFFFEBEE)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFC62828)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "Expense",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Monthly Spent",
                                fontSize = 10.sp,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format("%s%,.2f", currencySymbol, totalExpense),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Savings Progress & Rate
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Savings,
                            contentDescription = "Savings",
                            tint = if (netSavings >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Net Savings Rate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = String.format("%s%,.2f (%d%%)", currencySymbol, netSavings, savingsRate.toInt()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (netSavings >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                val animatedProgress by animateFloatAsState(
                    targetValue = (savingsRate / 100.0).toFloat(),
                    animationSpec = tween(800),
                    label = "savings_progress"
                )

                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (netSavings >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = when {
                        netSavings < 0 -> "⚠️ You spent more than you earned this month!"
                        savingsRate >= 30.0 -> "🚀 Outstanding savings! Keep building that nest egg."
                        savingsRate >= 10.0 -> "👍 Solid budget management. Great progress."
                        else -> "💡 Try reducing entertainment or shopping to boost your savings rate."
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top spending categories list (if any)
            if (topCategories.isNotEmpty()) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "Top Spending Categories",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    topCategories.forEach { (category, amount) ->
                        val catPercent = if (totalExpense > 0) (amount / totalExpense) * 100 else 0.0
                        val catColor = CategoryHelper.getColor(category)
                        val catIcon = CategoryHelper.getIcon(category)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(catColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = catIcon,
                                            contentDescription = category,
                                            tint = catColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = "$category (${catPercent.toInt()}%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = String.format("%s%,.2f", currencySymbol, amount),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            val animatedCatProgress by animateFloatAsState(
                                targetValue = (catPercent / 100.0).toFloat(),
                                animationSpec = tween(800),
                                label = "cat_progress"
                            )

                            LinearProgressIndicator(
                                progress = animatedCatProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(2.5.dp)),
                                color = catColor,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "No Expenses",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "No expenses logged for this month.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
