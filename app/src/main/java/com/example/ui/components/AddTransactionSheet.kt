package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import java.util.Calendar
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import com.example.data.api.GeminiManager
import com.example.data.api.ParsedBill
import java.io.File
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider
import android.Manifest
import android.content.Intent
import android.widget.Toast
import com.example.ui.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        amount: Double,
        category: String,
        type: String,
        notes: String,
        paymentMethod: String,
        creditCardBank: String?,
        isRecurring: Boolean,
        currency: String,
        scheduledDay: Int?
    ) -> Unit,
    modifier: Modifier = Modifier,
    initialTitle: String = "",
    initialAmount: Double = 0.0,
    initialCurrency: String = "INR",
    initialCategory: String? = null,
    initialNotes: String = "",
    initialType: String = "EXPENSE",
    initialPaymentMethod: String = "UPI",
    initialCreditCardBank: String? = null,
    initialIsRecurring: Boolean = false,
    initialScheduledDay: Int? = null,
    isEdit: Boolean = false
) {
    var title by remember { mutableStateOf(initialTitle) }
    var amountStr by remember { mutableStateOf(if (initialAmount > 0.0) initialAmount.toString() else "") }
    var notes by remember { mutableStateOf(initialNotes) }
    var selectedType by remember { mutableStateOf(initialType) } // "EXPENSE" or "INCOME"
    val categories = if (selectedType == "EXPENSE") {
        CategoryHelper.expenseCategories
    } else {
        CategoryHelper.incomeCategories
    }
    
    var selectedCategory by remember(selectedType) {
        mutableStateOf(initialCategory ?: categories.first())
    }
    var selectedCurrency by remember { mutableStateOf(initialCurrency) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    
    // Payment method states
    var paymentMethod by remember { mutableStateOf(initialPaymentMethod) } // "UPI", "CREDIT_CARD", "CASH"
    var creditCardBank by remember { mutableStateOf(initialCreditCardBank ?: "") }
    var payeeUpiId by remember { mutableStateOf("") }
    
    // Recurring state
    var isRecurring by remember { mutableStateOf(initialIsRecurring) }
    var scheduledDay by remember { mutableStateOf<Int?>(initialScheduledDay) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // AI Bill Analyser panel states
    var showAiBillScanner by remember { mutableStateOf(!isEdit) }
    var aiImageUri by remember { mutableStateOf<Uri?>(null) }
    var aiImageBase64 by remember { mutableStateOf("") }
    var aiImageName by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var aiStatusMessage by remember { mutableStateOf("") }
    
    // AI Category Auto-Suggestion States
    var isAiSuggestingCategory by remember { mutableStateOf(false) }
    var lastSuggestedTitle by remember { mutableStateOf("") }
    // Once the user manually picks a category (chip or dropdown), never let the debounced
    // AI suggestion below silently overwrite their choice.
    var userManuallySelectedCategory by remember { mutableStateOf(false) }

    // Automatically suggest expense category based on merchant name using Gemini Flash model
    LaunchedEffect(title, selectedType) {
        if (selectedType == "EXPENSE" && title.trim().length >= 3 && !userManuallySelectedCategory) {
            val cleanTitle = title.trim()
            if (cleanTitle != lastSuggestedTitle) {
                // Wait for the user to stop typing (debounce 1.2s)
                kotlinx.coroutines.delay(1200)
                if (title.trim() == cleanTitle) { // Ensure title hasn't changed during delay
                    isAiSuggestingCategory = true
                    val suggestion = GeminiManager.suggestCategory(cleanTitle)
                    if (suggestion != null && categories.contains(suggestion)) {
                        selectedCategory = suggestion
                        lastSuggestedTitle = cleanTitle
                    }
                    isAiSuggestingCategory = false
                }
            }
        }
    }
    
    var showAiImageChoiceDialog by remember { mutableStateOf(false) }
    
    val aiScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    val uri = pages[0].imageUri
                    try {
                        aiImageUri = uri
                        aiImageName = "scanned_receipt.jpg"
                        val base64 = com.example.utils.ImageUtils.getBase64FromUri(context, uri)
                        if (base64 != null) {
                            aiImageBase64 = base64
                            aiStatusMessage = "Analyzing..."
                            coroutineScope.launch {
                                val parsed = GeminiManager.analyzeBill(aiImageBase64, aiImageName)
                                if (parsed != null) {
                                    if (parsed.title == "Unknown Transaction") {
                                        aiStatusMessage = "The receipt was too blurry to read, please try again."
                                        android.widget.Toast.makeText(context, "The receipt was too blurry to read, please try again.", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        title = parsed.title
                                        amountStr = parsed.amount.toString()
                                        selectedCategory = parsed.category
                                        notes = parsed.notes
                                        selectedCurrency = parsed.currency
                                        aiStatusMessage = "Bill parsed successfully!"
                                    }
                                } else {
                                    aiStatusMessage = "Analysis failed."
                                }
                            }
                        } else {
                            aiStatusMessage = "Failed to read image bytes."
                        }
                    } catch (e: Exception) {
                        aiStatusMessage = "Error loading image: ${e.message}"
                    }
                }
            }
        } else {
            aiStatusMessage = "Camera capture cancelled."
        }
    }
    
    val aiCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            
            val scanner = GmsDocumentScanning.getClient(options)
            scanner.getStartScanIntent(context as Activity)
                .addOnSuccessListener { intentSender ->
                    aiScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { e ->
                    aiStatusMessage = "Failed to start scanner: ${e.message}"
                }
        } else {
            aiStatusMessage = "Camera permission denied."
        }
    }

    val aiGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                aiImageUri = it
                aiImageName = "uploaded_receipt.jpg"
                val base64 = com.example.utils.ImageUtils.getBase64FromUri(context, it)
                if (base64 != null) {
                    aiImageBase64 = base64
                    aiStatusMessage = "Analyzing..."
                    coroutineScope.launch {
                        val parsed = GeminiManager.analyzeBill(aiImageBase64, aiImageName)
                        if (parsed != null) {
                            title = parsed.title
                            amountStr = parsed.amount.toString()
                            selectedCategory = parsed.category
                            notes = parsed.notes
                            selectedCurrency = parsed.currency
                            aiStatusMessage = "Bill parsed successfully!"
                        } else {
                            aiStatusMessage = "Analysis failed."
                        }
                    }
                } else {
                    aiStatusMessage = "Failed to read image bytes."
                }
            } catch (e: Exception) {
                aiStatusMessage = "Error loading image: ${e.message}"
            }
        }
    }

    // Auto toggle recurring check if Category is "Personal Loan"
    LaunchedEffect(selectedCategory) {
        if (selectedCategory == "Personal Loan") {
            isRecurring = true
        }
    }

    val isValid = remember(title, amountStr) {
        val parsedAmount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
        title.isNotBlank() && parsedAmount > 0.0
    }

    val currencyList = listOf("INR", "USD", "EUR", "GBP", "JPY")
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("add_transaction_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = if (isEdit) "Edit Transaction" else "New Transaction",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // AI Bill Analyser Card Toggle
            BrutalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAiBillScanner = !showAiBillScanner }
                    .testTag("ai_bill_scanner_toggle"),
                backgroundColor = if (showAiBillScanner) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                accentColor = if (showAiBillScanner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                cornerRadius = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "AI Scanner",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Bill Analyser (Smart Scan)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (showAiBillScanner) "Tap to close AI Scanner" else "Autofill form by scanning a bill photo",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (showAiBillScanner) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Expand/Collapse",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded AI Scanner panel
            if (showAiBillScanner) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header for image upload
                    Text(
                        text = "Scan Receipt Image",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .clickable { showAiImageChoiceDialog = true }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (aiImageUri != null) Icons.Filled.CheckCircle else Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = if (aiImageUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (aiImageUri != null) aiImageName else "Choose Receipt Photo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (aiImageUri != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Image Selection Dialog
                    if (showAiImageChoiceDialog) {
                        AlertDialog(
                            onDismissRequest = { showAiImageChoiceDialog = false },
                            title = { Text("Select Receipt Source") },
                            text = { Text("How would you like to provide the receipt photo?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showAiImageChoiceDialog = false
                                    aiCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }) {
                                    Text("Camera")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showAiImageChoiceDialog = false
                                    aiGalleryLauncher.launch("image/*")
                                }) {
                                    Text("Gallery")
                                }
                            }
                        )
                    }

                    if (aiStatusMessage.isNotEmpty()) {
                        Text(
                            text = aiStatusMessage,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Scan Action button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isAnalyzing = true
                                aiStatusMessage = "Analyzing with Gemini..."
                                val extracted = GeminiManager.analyzeBill(aiImageBase64, aiImageName)
                                if (extracted != null) {
                                    title = extracted.title
                                    amountStr = extracted.amount.toString()
                                    selectedCurrency = extracted.currency
                                    selectedCategory = if (categories.contains(extracted.category)) extracted.category else categories.first()
                                    notes = extracted.notes
                                    aiStatusMessage = "Success! Form fields auto-filled."
                                    showAiBillScanner = false // Close scanner
                                } else {
                                    aiStatusMessage = "Failed to extract details."
                                }
                                isAnalyzing = false
                            }
                        },
                        enabled = !isAnalyzing && aiImageBase64.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("ai_scan_action_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Parsing details...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan and Autofill Form", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Segmented Type Selector (Income vs Expense)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedType == "EXPENSE",
                    onClick = { selectedType = "EXPENSE" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                        activeContentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.testTag("type_selector_expense")
                ) {
                    Text("Expense", fontWeight = FontWeight.Bold)
                }
                SegmentedButton(
                    selected = selectedType == "INCOME",
                    onClick = { selectedType = "INCOME" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("type_selector_income")
                ) {
                    Text("Income", fontWeight = FontWeight.Bold)
                }
            }

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title / Merchant") },
                placeholder = { Text("e.g., Starbucks Coffee, Personal Loan EMI, Salary") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_title"),
                shape = RoundedCornerShape(12.dp)
            )

            // Amount Field & Currency Dropdown combined
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Currency Selector Box
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { currencyMenuExpanded = true }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedCurrency,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Select Currency",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = currencyMenuExpanded,
                        onDismissRequest = { currencyMenuExpanded = false }
                    ) {
                        currencyList.forEach { cur ->
                            DropdownMenuItem(
                                text = { Text(cur, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    selectedCurrency = cur
                                    currencyMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount Text Field
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_amount"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Category Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Select Category",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isAiSuggestingCategory) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "AI guessing...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (lastSuggestedTitle.isNotEmpty() && title.trim().contains(lastSuggestedTitle, ignoreCase = true)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI Suggested",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "AI suggested",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Manual Category Override
                Box {
                    TextButton(onClick = { categoryMenuExpanded = true }) {
                        Text("Override Category", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    userManuallySelectedCategory = true
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Grid of categories
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { it }) { category ->
                    val isSelected = selectedCategory == category
                    val categoryColor = remember(category) { CategoryHelper.getColor(category) }
                    val categoryIcon = remember(category) { CategoryHelper.getIcon(category) }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) categoryColor.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) categoryColor else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedCategory = category
                                userManuallySelectedCategory = true
                            }
                            .padding(8.dp)
                            .testTag("category_button_$category"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) categoryColor.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = category,
                                    tint = categoryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Payment Method Section (Only if Expense)
            if (selectedType == "EXPENSE") {
                Text(
                    text = "Payment Method",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Row of payment methods to differentiate the mode of transactions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("UPI" to "UPI", "CREDIT_CARD" to "Credit Card", "CASH" to "Cash").forEach { (method, label) ->
                        val isSelected = paymentMethod == method
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
                                .clickable { paymentMethod = method }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // If Credit Card is selected, display Manual Bank input
                if (paymentMethod == "CREDIT_CARD") {
                    OutlinedTextField(
                        value = creditCardBank,
                        onValueChange = { creditCardBank = it },
                        label = { Text("Credit Card Bank Name") },
                        placeholder = { Text("e.g., HDFC, ICICI, Amex...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_cc_bank"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // If UPI is selected, offer a real payment button with optional Payee UPI VPA
                if (paymentMethod == "UPI") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = payeeUpiId,
                            onValueChange = { payeeUpiId = it },
                            label = { Text("Payee UPI ID (VPA) - Optional") },
                            placeholder = { Text("e.g., merchant@okaxis or phone@upi") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_payee_upi_id"),
                            shape = RoundedCornerShape(12.dp),
                            supportingText = {
                                Text(
                                    text = "Enter payee UPI ID to enable direct payment via external UPI apps.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        val numericAmount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                        if (numericAmount > 0.0) {
                            val isVpaProvided = payeeUpiId.trim().isNotEmpty()
                            Button(
                                onClick = {
                                    if (isVpaProvided) {
                                        val upiUri = Uri.parse("upi://pay?pa=${payeeUpiId.trim()}&pn=${Uri.encode(title)}&am=$numericAmount&cu=$selectedCurrency&tn=${Uri.encode(notes)}")
                                        val intent = Intent(Intent.ACTION_VIEW, upiUri)
                                        val chooser = Intent.createChooser(intent, "Pay with...")
                                        try {
                                            context.startActivity(chooser)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Please enter a valid Payee UPI ID (VPA) first", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isVpaProvided) Color(0xFF1A73E8) else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isVpaProvided) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pay now with Google Pay / UPI", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            // Recurring transaction checkbox & Calendar Scheduling
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isRecurring) Color(0xFFFFF3E0)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRecurring = !isRecurring },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isRecurring) Icons.Filled.Sync else Icons.Filled.SyncDisabled,
                        contentDescription = "Recurring Payment",
                        tint = if (isRecurring) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Schedule Monthly",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecurring) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Auto-logs this transaction every calendar month",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFE65100)
                        )
                    )
                }

                if (isRecurring) {
                    // Calendar / Date Picker Trigger
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.6f))
                            .border(1.dp, Color(0xFFE65100).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { showDatePicker = true }
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Select Date", tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                            Text(
                                text = if (scheduledDay != null) "Scheduled for day $scheduledDay of every month" else "Select Day for EMI Schedule",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (scheduledDay != null) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Date Picker Dialog
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val calendar = java.util.Calendar.getInstance()
                                calendar.timeInMillis = millis
                                scheduledDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            }
                            showDatePicker = false
                        }) {
                            Text("Select Day")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Notes field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                placeholder = { Text("Add payment notes, bill reference number...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_notes"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    val finalAmount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                    onSave(
                        title,
                        finalAmount,
                        selectedCategory,
                        selectedType,
                        notes,
                        paymentMethod,
                        if (paymentMethod == "CREDIT_CARD") creditCardBank else null,
                        isRecurring,
                        selectedCurrency,
                        scheduledDay
                    )
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_transaction_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == "EXPENSE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = if (isEdit) "Update Transaction" else "Add Transaction",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
