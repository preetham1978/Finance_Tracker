package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionItem(
    transaction: Transaction,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Highly optimized formatted states using remember
    val categoryColor = remember(transaction.category) {
        CategoryHelper.getColor(transaction.category)
    }
    
    val categoryIcon = remember(transaction.category) {
        CategoryHelper.getIcon(transaction.category)
    }
    
    val formattedAmount = remember(transaction.amount, transaction.type, transaction.currency) {
        val sign = if (transaction.type == "INCOME") "+" else "-"
        val symbol = when (transaction.currency) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            else -> "₹"
        }
        String.format("$sign$symbol%.2f", transaction.amount)
    }
    
    val amountColor = remember(transaction.type) {
        if (transaction.type == "INCOME") {
            Color(0xFF2E7D32) // Pure green for readability
        } else {
            Color(0xFFC62828) // Crimson for expenses
        }
    }
    
    val formattedDate = remember(transaction.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(transaction.timestamp))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onEdit() }
            .testTag("transaction_item_${transaction.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon with tinted background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = transaction.category,
                    tint = categoryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Transaction Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.category,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " • ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                val paymentLabel = when (transaction.paymentMethod) {
                    "UPI" -> "⚡ UPI"
                    "CREDIT_CARD" -> "💳 Credit Card${if (transaction.creditCardBank != null) ": " + transaction.creditCardBank else ""}"
                    "CASH" -> "💵 Cash"
                    "BANK_ACCOUNT" -> "🏦 Bank Account"
                    else -> "💵 ${transaction.paymentMethod.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}"
                }
                val paymentColor = when (transaction.paymentMethod) {
                    "UPI" -> Color(0xFF6200EE)
                    "CREDIT_CARD" -> MaterialTheme.colorScheme.primary
                    "CASH" -> Color(0xFF2E7D32)
                    else -> MaterialTheme.colorScheme.secondary
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = paymentLabel,
                    fontSize = 11.sp,
                    color = paymentColor,
                    fontWeight = FontWeight.Bold
                )
                
                if (transaction.isRecurring) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "🔁 Monthly Scheduled",
                        fontSize = 11.sp,
                        color = Color(0xFFD84315),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (transaction.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = transaction.notes,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Amount and Delete Action
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedAmount,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_transaction_button_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Transaction",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
