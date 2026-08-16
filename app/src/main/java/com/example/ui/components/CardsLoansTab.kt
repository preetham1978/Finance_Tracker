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

        // Investment Portfolio Section — Mutual Funds & Crypto get live
        // prices (AMFI / CoinGecko, both free, no API key); Stocks & Gold
        // are manually priced since there's no equivalent free, reliable
        // live source for those.
        val holdings by viewModel.holdings.collectAsState()
        val portfolioSummary by viewModel.portfolioSummary.collectAsState()
        var showAddHoldingDialog by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Investment Portfolio",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { showAddHoldingDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Holding", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (holdings.isNotEmpty()) {
            BrutalCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Invested", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("₹${String.format("%,.0f", portfolioSummary.totalInvested)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Current Value", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("₹${String.format("%,.0f", portfolioSummary.currentValue)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    val gainColor = if (portfolioSummary.gainLoss >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    val gainSign = if (portfolioSummary.gainLoss >= 0) "+" else ""
                    Text(
                        text = "$gainSign₹${String.format("%,.0f", portfolioSummary.gainLoss)} ($gainSign${String.format("%.1f", portfolioSummary.gainLossPercent)}%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = gainColor
                    )
                    TextButton(
                        onClick = { viewModel.refreshAllLivePrices() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh mutual fund & crypto prices", fontSize = 11.sp)
                    }
                }
            }

            holdings.forEach { holding ->
                HoldingCard(
                    holding = holding,
                    currentPrice = viewModel.currentPriceOf(holding),
                    onRefresh = { viewModel.refreshLivePrice(holding) },
                    onUpdateManualPrice = { price -> viewModel.updateManualPrice(holding, price) },
                    onDelete = { viewModel.deleteHolding(holding) }
                )
            }
        } else {
            BrutalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📈", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No investments tracked yet.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap + above to add a mutual fund, stock, crypto, or gold holding.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (showAddHoldingDialog) {
            AddHoldingDialog(
                onDismiss = { showAddHoldingDialog = false },
                onConfirm = { name, assetType, identifier, quantity, avgBuyPrice, manualPrice ->
                    viewModel.addHolding(
                        name = name,
                        assetType = assetType,
                        identifier = identifier,
                        quantity = quantity,
                        avgBuyPrice = avgBuyPrice,
                        currency = uiState.activeCurrency,
                        manualCurrentPrice = manualPrice
                    )
                    showAddHoldingDialog = false
                }
            )
        }
    }
}

private val ASSET_TYPES = listOf(
    "MUTUAL_FUND" to "Mutual Fund",
    "STOCK" to "Stock",
    "CRYPTO" to "Crypto",
    "GOLD" to "Gold",
    "OTHER" to "Other"
)

@Composable
private fun HoldingCard(
    holding: com.example.data.Holding,
    currentPrice: Double,
    onRefresh: () -> Unit,
    onUpdateManualPrice: (Double) -> Unit,
    onDelete: () -> Unit
) {
    val symbol = FinanceViewModel.currencySymbols[holding.currency] ?: "₹"
    val currentValue = holding.quantity * currentPrice
    val investedValue = holding.quantity * holding.avgBuyPrice
    val gain = currentValue - investedValue
    val gainPct = if (investedValue > 0) (gain / investedValue) * 100.0 else 0.0
    val gainColor = if (gain >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
    val isLivePriced = holding.assetType == "MUTUAL_FUND" || holding.assetType == "CRYPTO"

    var showEditPriceDialog by remember { mutableStateOf(false) }

    BrutalCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(holding.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = ASSET_TYPES.find { it.first == holding.assetType }?.second ?: holding.assetType,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    if (isLivePriced) {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh price", modifier = Modifier.size(16.dp))
                        }
                    } else {
                        IconButton(onClick = { showEditPriceDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Update price", modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${String.format("%,.2f", holding.quantity)} units @ $symbol${String.format("%,.2f", holding.avgBuyPrice)} avg",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$symbol${String.format("%,.2f", currentPrice)} now",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("$symbol${String.format("%,.2f", currentValue)}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                val gainSign = if (gain >= 0) "+" else ""
                Text(
                    text = "$gainSign$symbol${String.format("%,.2f", gain)} ($gainSign${String.format("%.1f", gainPct)}%)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = gainColor
                )
            }
        }
    }

    if (showEditPriceDialog) {
        UpdatePriceDialog(
            currentPrice = currentPrice,
            symbol = symbol,
            onDismiss = { showEditPriceDialog = false },
            onConfirm = { price ->
                onUpdateManualPrice(price)
                showEditPriceDialog = false
            }
        )
    }
}

@Composable
private fun UpdatePriceDialog(
    currentPrice: Double,
    symbol: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var priceStr by remember { mutableStateOf(if (currentPrice > 0) currentPrice.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Current Price") },
        text = {
            OutlinedTextField(
                value = priceStr,
                onValueChange = { priceStr = it },
                label = { Text("Current Price ($symbol per unit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { priceStr.toDoubleOrNull()?.let { onConfirm(it) } },
                enabled = priceStr.toDoubleOrNull() != null && priceStr.toDoubleOrNull()!! > 0
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddHoldingDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, assetType: String, identifier: String?, quantity: Double, avgBuyPrice: Double, manualPrice: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var assetType by remember { mutableStateOf("MUTUAL_FUND") }
    var identifier by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    var avgPriceStr by remember { mutableStateOf("") }
    var manualPriceStr by remember { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val isLiveType = assetType == "MUTUAL_FUND" || assetType == "CRYPTO"
    val isValid = name.isNotBlank() &&
        (quantityStr.toDoubleOrNull() ?: 0.0) > 0.0 &&
        (avgPriceStr.toDoubleOrNull() ?: 0.0) > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Investment Holding") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. HDFC Flexi Cap Fund, Bitcoin, Reliance)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Box {
                    OutlinedTextField(
                        value = ASSET_TYPES.find { it.first == assetType }?.second ?: assetType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { typeMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                    )
                    // Transparent clickable overlay so taps anywhere on the field open the menu
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { typeMenuExpanded = true }
                    )
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        ASSET_TYPES.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                assetType = key
                                typeMenuExpanded = false
                            })
                        }
                    }
                }

                if (isLiveType) {
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = {
                            Text(if (assetType == "MUTUAL_FUND") "AMFI Scheme Code (optional, enables live NAV)" else "CoinGecko Coin ID (e.g. bitcoin — optional, enables live price)")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it },
                        label = { Text("Quantity/Units") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = avgPriceStr,
                        onValueChange = { avgPriceStr = it },
                        label = { Text("Avg Buy Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = manualPriceStr,
                    onValueChange = { manualPriceStr = it },
                    label = { Text(if (isLiveType) "Current Price (optional fallback until first refresh)" else "Current Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        assetType,
                        identifier.ifBlank { null },
                        quantityStr.toDoubleOrNull() ?: 0.0,
                        avgPriceStr.toDoubleOrNull() ?: 0.0,
                        manualPriceStr.toDoubleOrNull() ?: 0.0
                    )
                },
                enabled = isValid
            ) {
                Text("Add Holding")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
