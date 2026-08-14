package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxPlannerTab(viewModel: FinanceViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Inputs for Tax Calculator
    var grossIncomeStr by remember { mutableStateOf("1200000") }
    var deductions80CStr by remember { mutableStateOf("150000") }
    var healthInsuranceStr by remember { mutableStateOf("25000") }
    var otherDeductionsStr by remember { mutableStateOf("50000") } // e.g. HRA / LTA
    
    val grossIncome = remember(grossIncomeStr) { grossIncomeStr.toDoubleOrNull() ?: 0.0 }
    val deductions80C = remember(deductions80CStr) { deductions80CStr.toDoubleOrNull() ?: 0.0 }
    val healthInsurance = remember(healthInsuranceStr) { healthInsuranceStr.toDoubleOrNull() ?: 0.0 }
    val otherDeductions = remember(otherDeductionsStr) { otherDeductionsStr.toDoubleOrNull() ?: 0.0 }

    // Computations
    val oldTax = remember(grossIncome, deductions80C, healthInsurance, otherDeductions) {
        calculateOldRegimeTax(grossIncome, deductions80C, healthInsurance, otherDeductions)
    }
    
    val newTax = remember(grossIncome) {
        calculateNewRegimeTax(grossIncome)
    }

    val taxSavings = remember(oldTax, newTax) {
        kotlin.math.abs(oldTax - newTax)
    }

    val recommendedRegime = remember(oldTax, newTax) {
        if (newTax <= oldTax) "New Regime" else "Old Regime"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("tax_planner_tab_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card
        Text(
            text = "Indian Income Tax Planner (FY 2024-25)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Compare tax regimes side-by-side and let Gemini AI curate a customized tax-saving roadmap.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Inputs Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Income & Exemption Declarations (INR)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = grossIncomeStr,
                    onValueChange = { grossIncomeStr = it },
                    label = { Text("Gross Annual Income (salaries, business, etc.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = deductions80CStr,
                        onValueChange = { deductions80CStr = it },
                        label = { Text("Sec 80C (max 1.5L)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = healthInsuranceStr,
                        onValueChange = { healthInsuranceStr = it },
                        label = { Text("Sec 80D Health") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = otherDeductionsStr,
                    onValueChange = { otherDeductionsStr = it },
                    label = { Text("Other Exemptions (HRA, Home Loan Interest etc.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Side-by-Side Tax Comparison Table
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Regime Comparison",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Calculation Field", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                    Text("Old Regime", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                    Text("New Regime", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Standard Deduction Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Standard Deduction", fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                    Text("₹50,000", fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                    Text("₹75,000", fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                }

                // 80C & 80D Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Deductions (80C, 80D)", fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                    val decValue = minOf(150000.0, deductions80C) + minOf(75000.0, healthInsurance) + otherDeductions
                    Text("₹${String.format("%,.0f", decValue)}", fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                    Text("Not Allowed", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                }

                // Taxable Income Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Taxable Income", fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                    val oldTaxable = maxOf(0.0, grossIncome - (50000.0 + minOf(150000.0, deductions80C) + minOf(75000.0, healthInsurance) + otherDeductions))
                    val newTaxable = maxOf(0.0, grossIncome - 75000.0)
                    Text("₹${String.format("%,.0f", oldTaxable)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                    Text("₹${String.format("%,.0f", newTaxable)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Net Tax Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Net Tax Payable", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1.2f))
                    Text("₹${String.format("%,.2f", oldTax)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFC62828), textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                    Text("₹${String.format("%,.2f", newTax)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFC62828), textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                }
            }
        }

        // Recommendation banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE8F5E9))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = Color(0xFF2E7D32))
                Column {
                    Text("Optimal Recommendation", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                    Text(
                        text = if (taxSavings > 0.0) "Save ₹${String.format("%,.2f", taxSavings)} by choosing the $recommendedRegime!"
                               else "Both tax regimes yield the exact same tax liability of ₹0.00!",
                        fontSize = 12.sp,
                        color = Color(0xFF1B5E20)
                    )
                }
            }
        }

        // AI Tax Advisor button & display
        Button(
            onClick = {
                viewModel.generateTaxSavingInsights(grossIncome, deductions80C, healthInsurance, otherDeductions)
            },
            enabled = !uiState.isAnalyzingTax,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isAnalyzingTax) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemini is curating tax plan...")
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get AI Tax Saving Plan")
            }
        }

        // Custom display card for AI Insights
        if (uiState.taxSavingInsights.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Gemini CA Tax Suggestions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Text(
                        text = parseMarkdown(uiState.taxSavingInsights),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

// Helper methods for Indian Income Tax
private fun calculateOldRegimeTax(income: Double, deductions80C: Double, healthInsurance: Double, otherDeductions: Double): Double {
    val sd = 50000.0
    val ded = sd + minOf(150000.0, deductions80C) + minOf(75000.0, healthInsurance) + otherDeductions
    val taxableIncome = maxOf(0.0, income - ded)

    if (taxableIncome <= 500000.0) return 0.0 // Rebate 87A

    var tax = 0.0
    if (taxableIncome <= 250000.0) {
        tax = 0.0
    } else if (taxableIncome <= 500000.0) {
        tax = (taxableIncome - 250000.0) * 0.05
    } else if (taxableIncome <= 1000000.0) {
        tax = 12500.0 + (taxableIncome - 500000.0) * 0.20
    } else {
        tax = 112500.0 + (taxableIncome - 1000000.0) * 0.30
    }
    return tax * 1.04 // 4% Cess
}

private fun calculateNewRegimeTax(income: Double): Double {
    val sd = 75000.0
    val taxableIncome = maxOf(0.0, income - sd)

    if (taxableIncome <= 700000.0) return 0.0 // Rebate 87A

    var tax = 0.0
    if (taxableIncome <= 300000.0) {
        tax = 0.0
    } else if (taxableIncome <= 700000.0) {
        tax = (taxableIncome - 300000.0) * 0.05
    } else if (taxableIncome <= 1000000.0) {
        tax = 20000.0 + (taxableIncome - 700000.0) * 0.10
    } else if (taxableIncome <= 1200000.0) {
        tax = 50000.0 + (taxableIncome - 1000000.0) * 0.15
    } else if (taxableIncome <= 1500000.0) {
        tax = 80000.0 + (taxableIncome - 1200000.0) * 0.20
    } else {
        tax = 140000.0 + (taxableIncome - 1500000.0) * 0.30
    }
    return tax * 1.04 // 4% Cess
}
