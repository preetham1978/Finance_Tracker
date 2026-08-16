package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

// Distinct, modern palette for identifying banks at a glance in the spend
// breakdown — deterministic per bank name so the same bank always gets the
// same color across app restarts, instead of every dot sharing the theme's
// secondary (yellow) color and becoming indistinguishable.
private val bankColorPalette = listOf(
    Color(0xFF1A73E8), // Blue
    Color(0xFF34A853), // Green
    Color(0xFFEA4335), // Red
    Color(0xFF9C27B0), // Purple
    Color(0xFFFF7043), // Orange
    Color(0xFF00ACC1), // Teal
    Color(0xFFEC407A), // Pink
    Color(0xFF6366F1)  // Indigo
)

private fun colorForBank(bank: String): Color {
    val index = Math.abs(bank.hashCode()) % bankColorPalette.size
    return bankColorPalette[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsLoansTab(viewModel: FinanceViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Currency Changer States
    val currencies = listOf("INR", "USD", "EUR", "GBP", "JPY")
    
    // Converter Local States
    var convAmountStr by remember { mutableStateOf("100") }
    var convFromCur by remember { mutableStateOf("USD") }
    var convToCur by remember { mutableStateOf("INR") }
    var fromMenuExpanded by remember { mutableStateOf(false) }
    var toMenuExpanded by remember { mutableStateOf(false) }
    
    val convertedResult = remember(convAmountStr, convFromCur, convToCur) {
        val amount = convAmountStr.toDoubleOrNull() ?: 0.0
        FinanceViewModel.convert(amount, convFromCur, convToCur)
    }

    // Dynamic Filtered Stats
    val creditCardTransactions = remember(uiState.transactions) {
        uiState.transactions.filter { it.paymentMethod == "CREDIT_CARD" }
    }
    
    val bankBreakdown = remember(creditCardTransactions, uiState.activeCurrency) {
        creditCardTransactions
            .groupBy { it.creditCardBank ?: "Other" }
            .mapValues { entry -> 
                entry.value.sumOf { FinanceViewModel.convert(it.amount, it.currency, uiState.activeCurrency) } 
            }
    }

    val recurringLoans = remember(uiState.transactions) {
        uiState.transactions.filter { it.category == "Personal Loan" && it.isRecurring }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("cards_loans_tab_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Default Display Currency Changer Section
        Text(
            text = "Currency Manager & Changer",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        BrutalCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Default Display Currency",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active Currency: ${uiState.activeCurrency} (${FinanceViewModel.currencySymbols[uiState.activeCurrency]})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currencies.forEach { cur ->
                        val isSelected = uiState.activeCurrency == cur
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.changeActiveCurrency(cur) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$cur ${FinanceViewModel.currencySymbols[cur]}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Live Currency Converter Utility Widget
        BrutalCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Interactive Currency Converter", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Converter amount field
                    OutlinedTextField(
                        value = convAmountStr,
                        onValueChange = { convAmountStr = it },
                        label = { Text("Enter Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // From selector
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { fromMenuExpanded = true }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(convFromCur, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = fromMenuExpanded, onDismissRequest = { fromMenuExpanded = false }) {
                            currencies.forEach { cur ->
                                DropdownMenuItem(text = { Text(cur, fontWeight = FontWeight.Bold) }, onClick = {
                                    convFromCur = cur
                                    fromMenuExpanded = false
                                })
                            }
                        }
                    }

                    // Swap Icon
                    IconButton(
                        onClick = {
                            val temp = convFromCur
                            convFromCur = convToCur
                            convToCur = temp
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap")
                    }

                    // To selector
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { toMenuExpanded = true }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(convToCur, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = toMenuExpanded, onDismissRequest = { toMenuExpanded = false }) {
                            currencies.forEach { cur ->
                                DropdownMenuItem(text = { Text(cur, fontWeight = FontWeight.Bold) }, onClick = {
                                    convToCur = cur
                                    toMenuExpanded = false
                                })
                            }
                        }
                    }
                }

                // Result Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Result: ${FinanceViewModel.currencySymbols[convFromCur]}$convAmountStr = ${FinanceViewModel.currencySymbols[convToCur]}${String.format("%.2f", convertedResult)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Indian Credit Card Spending Stats Section
        Text(
            text = "Indian Credit Cards Spending Tracker",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (bankBreakdown.isEmpty()) {
            BrutalCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("💳", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No credit card payments registered yet.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Add credit card payments in Dashboard with manual bank input to see detailed stats here.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            BrutalCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Spend Breakdown by Manual Bank Input (${uiState.activeCurrency})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    bankBreakdown.forEach { (bank, total) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colorForBank(bank))
                                )
                                Text(bank, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Text(
                                text = "${FinanceViewModel.currencySymbols[uiState.activeCurrency]}${String.format("%,.2f", total)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Recurring monthly Loan scheduling panel
        Text(
            text = "Personal Loan & EMIs Scheduled (Monthly)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        BrutalCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = Color(0xFFD84315))
                    Text("Auto-Scheduling Calendar Policy", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Text(
                    text = "Configure personal loan payouts matching monthly amortization schedules. Use the Dashboard to add manual inputs with calendar scheduling.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show scheduled list
                if (recurringLoans.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Text(
                        "Active Scheduled Loans:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFD84315)
                    )

                    recurringLoans.take(10).forEach { loan ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(loan.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                val dayInfo = if (loan.scheduledDayOfMonth != null) "Due on day ${loan.scheduledDayOfMonth}" else "Monthly"
                                Text(
                                    text = "$dayInfo • Next: ${SimpleDateFormat("MMM", Locale.getDefault()).format(Date())}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val symbol = FinanceViewModel.currencySymbols[loan.currency] ?: "₹"
                            Text(
                                text = "-$symbol${String.format("%,.2f", loan.amount)}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    Text(
                        "No recurring loans scheduled. Go to Dashboard > + > Category: Personal Loan > Schedule Monthly.",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
