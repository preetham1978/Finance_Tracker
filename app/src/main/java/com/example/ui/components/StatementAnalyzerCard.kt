package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Transaction
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatementAnalyzerCard(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    
    var isExpanded by remember { mutableStateOf(false) }
    
    // Parsed transactions state
    var parsedTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isParsing by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isParsing = true
            coroutineScope.launch {
                try {
                    val parsedList = parseCsvFile(context, uri)
                    if (parsedList.isNotEmpty()) {
                        parsedTransactions = parsedList
                        successMessage = "Successfully parsed ${parsedList.size} transactions!"
                    } else {
                        Toast.makeText(context, "Could not find any valid transactions in CSV.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isParsing = false
                }
            }
        }
    }

    BrutalCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("statement_analyzer_card"),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Export & Share",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Vantage Tools & Analyzer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Export PDF/CSV • Share on WhatsApp • Parse Statements",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // --- SECTION 1: Export Ledger ---
                    Text(
                        text = "1. Export Current Ledger",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        Button(
                            onClick = {
                                val allTx = uiState.filteredTransactions
                                if (allTx.isEmpty()) {
                                    Toast.makeText(context, "No transactions to export.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val pdfUri = ExportHelper.generatePdf(context, allTx)
                                if (pdfUri != null) {
                                    ExportHelper.shareFile(
                                        context = context,
                                        uri = pdfUri,
                                        mimeType = "application/pdf",
                                        message = "My Vantage Finance ledger statement report 📈",
                                        targetWhatsApp = false
                                    )
                                } else {
                                    Toast.makeText(context, "Failed to generate PDF.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                val allTx = uiState.filteredTransactions
                                if (allTx.isEmpty()) {
                                    Toast.makeText(context, "No transactions to export.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val csvUri = ExportHelper.generateCsv(context, allTx)
                                if (csvUri != null) {
                                    ExportHelper.shareFile(
                                        context = context,
                                        uri = csvUri,
                                        mimeType = "text/csv",
                                        message = "My Vantage Finance CSV ledger export 📊",
                                        targetWhatsApp = false
                                    )
                                } else {
                                    Toast.makeText(context, "Failed to generate CSV.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Filled.GridOn, contentDescription = "CSV", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // --- SECTION 2: WhatsApp Quick Share ---
                    Text(
                        text = "2. Quick Share via WhatsApp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Button(
                        onClick = {
                            val allTx = uiState.filteredTransactions
                            if (allTx.isEmpty()) {
                                Toast.makeText(context, "No transactions to share.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            // Generate PDF report by default for elegant sharing
                            val pdfUri = ExportHelper.generatePdf(context, allTx)
                            if (pdfUri != null) {
                                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                                val message = "Hi! 📊 Here is my Vantage Finance ledger statement report generated on $dateStr. Powered by Gemini AI Studio. ✨"
                                ExportHelper.shareFile(
                                    context = context,
                                    uri = pdfUri,
                                    mimeType = "application/pdf",
                                    message = message,
                                    targetWhatsApp = true
                                )
                            } else {
                                Toast.makeText(context, "Failed to generate report file.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366), // Official WhatsApp Green
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("whatsapp_share_button")
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "WhatsApp Share", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Share Ledger Report on WhatsApp", fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // --- SECTION 3: Statement Import & Analysis ---
                    Text(
                        text = "3. Bank Statement Parser & Analyser (CSV)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { filePickerLauncher.launch("text/comma-separated-values") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(42.dp)
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Upload", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select CSV File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                parsedTransactions = getSampleCsvTransactions()
                                successMessage = "Successfully preloaded sample UPI/Card bank statement!"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Sample", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Sample CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // Status messages
                    if (isParsing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Analyzing statement data...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    successMessage?.let { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE8F5E9))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                            Text(text = msg, fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // --- PARSED DISPLAY & BEAUTIFUL PIE CHART ---
                    if (parsedTransactions.isNotEmpty()) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        Text(
                            text = "Parsed Statement Analytics",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val categoryBreakdown = remember(parsedTransactions) {
                            parsedTransactions
                                .filter { it.type == "EXPENSE" }
                                .groupBy { it.category }
                                .mapValues { it.value.sumOf { t -> t.amount } }
                        }
                        
                        // Pie Chart Composable
                        if (categoryBreakdown.isNotEmpty()) {
                            BeautifulPieChart(categoryBreakdown = categoryBreakdown)
                        }
                        
                        // Transaction Preview List
                        Text(
                            text = "Transactions found in statement (${parsedTransactions.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            parsedTransactions.forEach { tx ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tx.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(tx.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                            Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            val badgeText = when (tx.paymentMethod) {
                                                "UPI" -> "⚡ UPI"
                                                "CREDIT_CARD" -> "💳 Card"
                                                "CASH" -> "💵 Cash"
                                                else -> "🏦 Bank"
                                            }
                                            Text(badgeText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    
                                    val prefix = if (tx.type == "INCOME") "+" else "-"
                                    val amountColor = if (tx.type == "INCOME") Color(0xFF2E7D32) else Color(0xFFC62828)
                                    Text(
                                        text = "$prefix₹${String.format("%,.1f", tx.amount)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = amountColor
                                    )
                                }
                            }
                        }
                        
                        // Import Button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    parsedTransactions.forEach { tx ->
                                        viewModel.addTransaction(
                                            title = tx.title,
                                            amount = tx.amount,
                                            category = tx.category,
                                            type = tx.type,
                                            notes = tx.notes,
                                            paymentMethod = tx.paymentMethod,
                                            creditCardBank = tx.creditCardBank,
                                            isRecurring = tx.isRecurring,
                                            currency = tx.currency
                                        )
                                    }
                                    Toast.makeText(context, "Successfully imported ${parsedTransactions.size} transactions to your ledger!", Toast.LENGTH_LONG).show()
                                    parsedTransactions = emptyList()
                                    successMessage = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "Import", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Transactions to Ledger", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BeautifulPieChart(
    categoryBreakdown: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    val sortedData = remember(categoryBreakdown) {
        categoryBreakdown.toList().sortedByDescending { it.second }
    }
    
    val totalSum = remember(sortedData) {
        sortedData.sumOf { it.second }
    }
    
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(key1 = categoryBreakdown) {
        animationTriggered = true
    }
    
    val animateProgress by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "pie_chart_animation"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Draw the circular Pie Chart sectors
        Box(
            modifier = Modifier
                .size(110.dp)
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(100.dp)) {
                var startAngle = -90f
                sortedData.forEach { (category, amount) ->
                    val sweepAngle = ((amount / totalSum) * 360f).toFloat() * animateProgress
                    
                    // Draw filled arc sector (pie slice)
                    drawArc(
                        color = CategoryHelper.getColor(category),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true
                    )
                    startAngle += sweepAngle
                }
            }
        }
        
        // Detailed legends with percentage
        Column(
            modifier = Modifier.weight(1.2f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sortedData.take(4).forEach { (category, amount) ->
                val color = CategoryHelper.getColor(category)
                val percent = (amount / totalSum) * 100
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = "$category (${String.format("%.1f", percent)}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (sortedData.size > 4) {
                Text(
                    text = "+ ${sortedData.size - 4} other categories",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Parses a simple CSV from standard Uri stream
 */
private fun parseCsvFile(context: Context, uri: Uri): List<Transaction> {
    val list = mutableListOf<Transaction>()
    try {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(uri) ?: return emptyList()
        val reader = BufferedReader(InputStreamReader(inputStream))
        
        var line: String?
        var isHeader = true
        
        // Header indices mapping dynamically
        var titleIdx = 2
        var amountIdx = 3
        var categoryIdx = 5
        var typeIdx = 6
        var methodIdx = 7
        var currencyIdx = 4
        var notesIdx = 9
        
        while (reader.readLine().also { line = it } != null) {
            val columns = line!!.split(",").map { it.trim().removeSurrounding("\"") }
            if (columns.size < 2) continue
            
            if (isHeader) {
                isHeader = false
                // Check if columns match expected headers and map dynamically
                columns.forEachIndexed { index, head ->
                    when (head.lowercase()) {
                        "title", "particulars", "merchant", "payee" -> titleIdx = index
                        "amount", "value" -> amountIdx = index
                        "category", "sector" -> categoryIdx = index
                        "type" -> typeIdx = index
                        "payment method", "method", "paymentmethod" -> methodIdx = index
                        "currency" -> currencyIdx = index
                        "notes", "remark", "remarks" -> notesIdx = index
                    }
                }
                continue
            }
            
            try {
                val title = columns.getOrNull(titleIdx) ?: "Transaction"
                val amount = columns.getOrNull(amountIdx)?.toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) continue
                
                var category = columns.getOrNull(categoryIdx) ?: "Other"
                // Clean and ensure category exists
                if (category !in CategoryHelper.expenseCategories && category !in CategoryHelper.incomeCategories) {
                    category = "Other"
                }
                
                val type = columns.getOrNull(typeIdx)?.uppercase() ?: "EXPENSE"
                val paymentMethod = columns.getOrNull(methodIdx)?.uppercase() ?: "UPI"
                val currency = columns.getOrNull(currencyIdx)?.uppercase() ?: "INR"
                val notes = columns.getOrNull(notesIdx) ?: ""
                
                list.add(
                    Transaction(
                        title = title,
                        amount = amount,
                        category = category,
                        timestamp = System.currentTimeMillis(),
                        notes = notes,
                        type = type,
                        paymentMethod = paymentMethod,
                        currency = currency
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        reader.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

private fun getSampleCsvTransactions(): List<Transaction> {
    return listOf(
        Transaction(
            title = "Swiggy Delivery Food",
            amount = 450.0,
            category = "Food",
            timestamp = System.currentTimeMillis() - 86400000,
            notes = "Dinner with friends",
            type = "EXPENSE",
            paymentMethod = "UPI",
            currency = "INR"
        ),
        Transaction(
            title = "Amazon India Shopping",
            amount = 12500.0,
            category = "Shopping",
            timestamp = System.currentTimeMillis() - (86400000 * 2),
            notes = "Noise cancelling headphones",
            type = "EXPENSE",
            paymentMethod = "CREDIT_CARD",
            creditCardBank = "OneCard",
            currency = "INR"
        ),
        Transaction(
            title = "Apartment Rent Payment",
            amount = 18000.0,
            category = "Bills & Utilities",
            timestamp = System.currentTimeMillis() - (86400000 * 3),
            notes = "June rent payment",
            type = "EXPENSE",
            paymentMethod = "UPI",
            currency = "INR"
        ),
        Transaction(
            title = "Salary Credited",
            amount = 75000.0,
            category = "Salary",
            timestamp = System.currentTimeMillis() - (86400000 * 4),
            notes = "Monthly payout",
            type = "INCOME",
            paymentMethod = "CASH",
            currency = "INR"
        ),
        Transaction(
            title = "Uber India Ride",
            amount = 320.0,
            category = "Travel & Transport",
            timestamp = System.currentTimeMillis() - (86400000 * 5),
            notes = "Commute to office",
            type = "EXPENSE",
            paymentMethod = "UPI",
            currency = "INR"
        ),
        Transaction(
            title = "Netflix Subscription Premium",
            amount = 649.0,
            category = "Entertainment",
            timestamp = System.currentTimeMillis() - (86400000 * 6),
            notes = "Premium 4K streaming plan",
            type = "EXPENSE",
            paymentMethod = "CREDIT_CARD",
            creditCardBank = "OneCard",
            currency = "INR"
        )
    )
}
