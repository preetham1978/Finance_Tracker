package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider
import android.Manifest
import android.content.Intent
import android.widget.Toast
import com.example.data.api.GeminiManager
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.example.data.api.ParsedBill
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerTab(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Receipt Scan states
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBase64 by remember { mutableStateOf("") }
    var selectedImageName by remember { mutableStateOf("") }
    var isAnalyzingBill by remember { mutableStateOf(false) }
    var parsedBillResult by remember { mutableStateOf<ParsedBill?>(null) }
    var analysisStatusMessage by remember { mutableStateOf("") }

    var receiptThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var showImageChoiceDialog by remember { mutableStateOf(false) }

    // Decodes a small, memory-safe preview thumbnail from the picked/scanned
    // receipt so the user sees the actual photo instead of just a filename.
    fun loadThumbnail(uri: Uri) {
        receiptThumbnail = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // Bottom Sheet Addition Form integration
    var showAddSheetFromScanner by remember { mutableStateOf(false) }
    var prefilledTitle by remember { mutableStateOf("") }
    var prefilledAmount by remember { mutableStateOf(0.0) }
    var prefilledCurrency by remember { mutableStateOf("INR") }
    var prefilledCategory by remember { mutableStateOf("Other") }
    var scannerPayeeUpiId by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    // Launcher for Camera
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    val uri = pages[0].imageUri
                    try {
                        selectedImageUri = uri
                        selectedImageName = "scanned_receipt.jpg"
                        loadThumbnail(uri)
                        val base64 = com.example.utils.ImageUtils.getBase64FromUri(context, uri)
                        if (base64 != null) {
                            selectedImageBase64 = base64
                            analysisStatusMessage = "Analyzing..."
                            scope.launch {
                                val parsed = GeminiManager.analyzeBill(selectedImageBase64, selectedImageName)
                                if (parsed != null) {
                                    if (parsed.title == "Unknown Transaction") {
                                        parsedBillResult = null
                                        analysisStatusMessage = "The receipt was too blurry to read, please try again."
                                        android.widget.Toast.makeText(context, "The receipt was too blurry to read, please try again.", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        parsedBillResult = parsed
                                        prefilledTitle = parsed.title
                                        prefilledAmount = parsed.amount
                                        prefilledCurrency = parsed.currency
                                        prefilledCategory = parsed.category
                                        analysisStatusMessage = "Bill parsed successfully!"
                                    }
                                } else {
                                    analysisStatusMessage = "Analysis failed."
                                }
                            }
                        } else {
                            analysisStatusMessage = "Failed to read image bytes."
                        }
                    } catch (e: Exception) {
                        analysisStatusMessage = "Error loading image: ${e.message}"
                    }
                }
            }
        } else {
            analysisStatusMessage = "Camera capture cancelled."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
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
                    // Same App Lock trip-out guard as AddTransactionSheet's
                    // scanner: leaving MainActivity for the scanner UI
                    // triggers onStop() which would otherwise re-lock the app
                    // and blow away this screen's in-progress scan state.
                    AppLockManager.suppressNextLock = true
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { e ->
                    analysisStatusMessage = "Failed to start scanner: ${e.message}"
                }
        } else {
            analysisStatusMessage = "Camera permission denied."
        }
    }

    // Launcher for Gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                selectedImageUri = it
                selectedImageName = "uploaded_receipt.jpg"
                loadThumbnail(it)
                val base64 = com.example.utils.ImageUtils.getBase64FromUri(context, it)
                if (base64 != null) {
                    selectedImageBase64 = base64
                    analysisStatusMessage = "Analyzing..."
                    scope.launch {
                        val parsed = GeminiManager.analyzeBill(selectedImageBase64, selectedImageName)
                        if (parsed != null) {
                            if (parsed.title == "Unknown Transaction") {
                                parsedBillResult = null
                                analysisStatusMessage = "The receipt was too blurry to read, please try again."
                                android.widget.Toast.makeText(context, "The receipt was too blurry to read, please try again.", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                parsedBillResult = parsed
                                prefilledTitle = parsed.title
                                prefilledAmount = parsed.amount
                                prefilledCurrency = parsed.currency
                                prefilledCategory = parsed.category
                                analysisStatusMessage = "Bill parsed successfully!"
                            }
                        } else {
                            analysisStatusMessage = "Analysis failed."
                        }
                    }
                } else {
                    analysisStatusMessage = "Failed to read image bytes."
                }
            } catch (e: Exception) {
                analysisStatusMessage = "Error loading image: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("scanner_tab_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Section
        Text(
            text = "AI Smart Scanner",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Instantly analyze printed paper bills, invoices, receipts, and Google Pay screenshots with Gemini AI.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        BrutalCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("AI Receipt & GPay Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    "Upload a receipt photo or Google Pay transaction screenshot. Gemini AI will instantly extract details like merchants, amounts, and categories.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Upload receipt photo UI — bold dashed-style outline matching the
        // "Vantage Neo" hard-border treatment instead of a faint 1dp hint.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { showImageChoiceDialog = true }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedImageUri != null) {
                val thumbnail = receiptThumbnail
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Selected receipt preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Text(
                    text = selectedImageName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Click to replace photo",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = "Upload",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Choose Receipt or GPay Screenshot",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Supports JPEG, PNG up to 5MB",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Image Selection Dialog
        if (showImageChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showImageChoiceDialog = false },
                title = { Text("Select Receipt Source") },
                text = { Text("How would you like to provide the receipt photo?") },
                confirmButton = {
                    TextButton(onClick = {
                        showImageChoiceDialog = false
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Text("Camera")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImageChoiceDialog = false
                        AppLockManager.suppressNextLock = true
                        galleryLauncher.launch("image/*")
                    }) {
                        Text("Gallery")
                    }
                }
            )
        }

        if (analysisStatusMessage.isNotEmpty()) {
            Text(
                text = analysisStatusMessage,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Trigger button
        Button(
            onClick = {
                coroutineScope.launch {
                    isAnalyzingBill = true
                    parsedBillResult = null
                    val result = GeminiManager.analyzeBill(selectedImageBase64, selectedImageName)
                    parsedBillResult = result
                    isAnalyzingBill = false
                }
            },
            enabled = !isAnalyzingBill && selectedImageBase64.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("trigger_ai_analysis_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isAnalyzingBill) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemini is parsing...")
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze with Gemini AI")
            }
        }

        // Parsed Result card
        parsedBillResult?.let { bill ->
            BrutalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Extracted Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CategoryHelper.getColor(bill.category).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = bill.category,
                                color = CategoryHelper.getColor(bill.category),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Merchant", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(bill.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Amount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val symbol = when (bill.currency) {
                            "INR" -> "₹"
                            "USD" -> "$"
                            "EUR" -> "€"
                            "GBP" -> "£"
                            else -> "₹"
                        }
                        Text("$symbol${bill.amount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFC62828))
                    }

                    if (bill.notes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Items Extracted:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(bill.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Button(
                        onClick = {
                            prefilledTitle = bill.title
                            prefilledAmount = bill.amount
                            prefilledCurrency = bill.currency
                            prefilledCategory = bill.category
                            showAddSheetFromScanner = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Transaction to History")
                    }

                    OutlinedTextField(
                        value = scannerPayeeUpiId,
                        onValueChange = { scannerPayeeUpiId = it },
                        label = { Text("Recipient UPI ID (VPA) - Optional") },
                        placeholder = { Text("e.g., merchant@okaxis or phone@upi") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_scanner_payee_upi_id"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val isVpaProvided = scannerPayeeUpiId.trim().isNotEmpty()

                    Button(
                        onClick = {
                            if (isVpaProvided) {
                                val upiUri = Uri.parse("upi://pay?pa=${scannerPayeeUpiId.trim()}&pn=${Uri.encode(bill.title)}&am=${bill.amount}&cu=${bill.currency}&tn=${Uri.encode(bill.notes)}")
                                val intent = Intent(Intent.ACTION_VIEW, upiUri)
                                val chooser = Intent.createChooser(intent, "Pay now via...")
                                try {
                                    context.startActivity(chooser)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Please enter a valid Payee UPI ID (VPA) first", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVpaProvided) Color(0xFF1A73E8) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isVpaProvided) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay Now with Google Pay", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Sheet popup to add scanned item
    if (showAddSheetFromScanner) {
        AddTransactionSheet(
            onDismiss = { showAddSheetFromScanner = false },
            initialTitle = prefilledTitle,
            initialAmount = prefilledAmount,
            initialCurrency = prefilledCurrency,
            initialCategory = prefilledCategory,
            onSave = { title, amount, category, type, notes, method, cardBank, isRec, cur, scheduledDay ->
                viewModel.addTransaction(
                    title = title,
                    amount = amount,
                    category = category,
                    type = type,
                    notes = notes,
                    paymentMethod = method,
                    creditCardBank = cardBank,
                    isRecurring = isRec,
                    currency = cur,
                    scheduledDay = scheduledDay
                )
                showAddSheetFromScanner = false
                parsedBillResult = null
            }
        )
    }
}
