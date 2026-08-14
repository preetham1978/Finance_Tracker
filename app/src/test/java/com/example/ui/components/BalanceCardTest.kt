package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for BalanceCard - the Dashboard's headline balance/income/expense summary.
 *
 * This composable takes plain values (no ViewModel), so it's a cheap, low-risk target for
 * Robolectric UI tests, unlike the tab composables that require a real FinanceViewModel
 * (see CardsLoansTab / ScannerTab, deliberately left to manual verification for the same
 * reason CategoryManagementTab was: FinanceViewModel eagerly touches FirebaseAuth and a real
 * Room-backed repository in its init block).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BalanceCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysFormattedIndianRupeeBalance_incomeAndExpense() {
        composeTestRule.setContent {
            BalanceCard(
                totalBalance = 15000.50,
                totalIncome = 20000.0,
                totalExpense = 5000.0,
                creditCardSpending = 0.0,
                activeCurrency = "INR"
            )
        }

        composeTestRule.onNodeWithText("₹15,000.50").assertExists()
        composeTestRule.onNodeWithText("₹20,000.00").assertExists()
        composeTestRule.onNodeWithText("₹5,000.00").assertExists()
    }

    @Test
    fun negativeBalance_isPrefixedWithMinusSign() {
        composeTestRule.setContent {
            BalanceCard(
                totalBalance = -250.75,
                totalIncome = 100.0,
                totalExpense = 350.75,
                activeCurrency = "INR"
            )
        }

        composeTestRule.onNodeWithText("-₹250.75").assertExists()
    }

    @Test
    fun creditCardSpending_isExcludedFromDisplayedExpenseFigure() {
        // BalanceCard's "Expense" tile deliberately shows non-credit-card expense only
        // (totalExpense - creditCardSpending) - the tooltip explains this excludes pending
        // credit card payments. Pin down that arithmetic explicitly.
        composeTestRule.setContent {
            BalanceCard(
                totalBalance = 10000.0,
                totalIncome = 8000.0,
                totalExpense = 5000.0,
                creditCardSpending = 2000.0,
                activeCurrency = "INR"
            )
        }

        // 5000 - 2000 = 3000 shown as the non-CC expense figure.
        composeTestRule.onNodeWithText("₹3,000.00").assertExists()
    }

    @Test
    fun usdCurrency_usesDollarSymbolInsteadOfRupee() {
        composeTestRule.setContent {
            BalanceCard(
                totalBalance = 1200.0,
                totalIncome = 1500.0,
                totalExpense = 300.0,
                activeCurrency = "USD"
            )
        }

        composeTestRule.onNodeWithText("$1,200.00").assertExists()
    }
}
