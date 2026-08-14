package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryHelper {
    val expenseCategories = listOf(
        "Food",
        "Shopping",
        "Bills & Utilities",
        "Entertainment",
        "Travel & Transport",
        "Health & Fitness",
        "Personal Loan",
        "Other"
    )

    val incomeCategories = listOf(
        "Salary",
        "Freelance",
        "Investments",
        "Other Income"
    )

    fun getIcon(category: String): ImageVector {
        return when (category) {
            "Salary" -> Icons.Filled.Work
            "Freelance" -> Icons.Filled.Laptop
            "Investments" -> Icons.Filled.ShowChart
            "Other Income" -> Icons.Filled.Savings
            "Food" -> Icons.Filled.Restaurant
            "Shopping" -> Icons.Filled.ShoppingBag
            "Bills & Utilities" -> Icons.Filled.ReceiptLong
            "Entertainment" -> Icons.Filled.LocalActivity
            "Travel & Transport" -> Icons.Filled.DirectionsCar
            "Health & Fitness" -> Icons.Filled.Favorite
            "Personal Loan" -> Icons.Filled.AccountBalance
            else -> Icons.Filled.Category
        }
    }

    fun getColor(category: String): Color {
        return when (category) {
            "Salary" -> Color(0xFF2E7D32)      // Forest Green
            "Freelance" -> Color(0xFF00C853)   // Emerald Green
            "Investments" -> Color(0xFF00B0FF) // Light Blue
            "Other Income" -> Color(0xFF00BFA5)// Teal
            "Food" -> Color(0xFFFF9100)        // Orange
            "Shopping" -> Color(0xFF2979FF)    // Blue
            "Bills & Utilities" -> Color(0xFFFF1744) // Red
            "Entertainment" -> Color(0xFFD500F9) // Purple / Pink
            "Travel & Transport" -> Color(0xFF00E5FF) // Cyan
            "Health & Fitness" -> Color(0xFF651FFF) // Deep Purple
            "Personal Loan" -> Color(0xFFD84315) // Deep Orange / Red-Orange for Loan EMI
            else -> Color(0xFF90A4AE)          // Slate Gray
        }
    }
}
