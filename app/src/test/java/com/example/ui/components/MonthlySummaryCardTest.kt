package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for MonthlySummaryCard - the "Monthly Ledger Summary" widget (income/expense
 * totals, savings rate, top spending categories).
 *
 * Takes a plain List<Transaction> plus a currency code, and only calls the *static*
 * FinanceViewModel.convert / currencySymbols helpers (companion object, no instance) - so
 * like BalanceCard and DonutChart it's directly testable without the Firebase/Room coupling
 * risk that ruled out testing the tab-level composables (CardsLoansTab, ScannerTab,
 * CategoryManagementTab) that take a live FinanceViewModel.
 *
 * A fixed timestamp is used for every transaction so all fixtures land in the same
 * calendar month regardless of when the test suite actually runs.
 *
 * No performScrollTo() calls here deliberately: unlike AddTransactionSheet/LoginScreen,
 * MonthlySummaryCard's root Column has no verticalScroll/LazyColumn at all, so none of its
 * content has a scrollable ancestor - calling performScrollTo() on any node here throws
 * "Semantic Node has no parent layout with a Scroll SemanticsAction" instead of no-op'ing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MonthlySummaryCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Fixed point in time (well within a single month) so grouping-by-month is deterministic.
    private val fixedTimestamp = 1700000000000L // 2023-11-14, arbitrary but stable.

    @Test
    fun singleMonthTransactions_showsAggregatedIncomeExpenseAndSavingsRate() {
        val transactions = listOf(
            Transaction(title = "Salary", amount = 20000.0, category = "Salary", type = "INCOME", timestamp = fixedTimestamp, currency = "INR"),
            Transaction(title = "Groceries", amount = 3000.0, category = "Food", type = "EXPENSE", timestamp = fixedTimestamp, currency = "INR"),
            Transaction(title = "New Shoes", amount = 2000.0, category = "Shopping", type = "EXPENSE", timestamp = fixedTimestamp, currency = "INR")
        )

        composeTestRule.setContent {
            MonthlySummaryCard(transactions = transactions, activeCurrency = "INR")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("monthly_summary_card").assertExists()

        // Income 20000, expense 3000+2000=5000, net savings 15000 -> savings rate 75%.
        composeTestRule.onNodeWithText("₹20,000.00").assertExists()
        composeTestRule.onNodeWithText("₹5,000.00").assertExists()
        composeTestRule.onNodeWithText("₹15,000.00 (75%)").assertExists()

        // Top spending categories, sorted by amount descending: Food (60%), Shopping (40%).
        composeTestRule.onNodeWithText("Food (60%)").assertExists()
        composeTestRule.onNodeWithText("Shopping (40%)").assertExists()
    }

    @Test
    fun noTransactions_showsZeroStatsAndNoExpensesMessage() {
        composeTestRule.setContent {
            MonthlySummaryCard(transactions = emptyList(), activeCurrency = "INR")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("monthly_summary_card").assertExists()
        // Both "Monthly Income" and "Monthly Spent" mini-cards read ₹0.00 with no transactions.
        val zeroValueNodes = composeTestRule.onAllNodesWithText("₹0.00")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertEquals(2, zeroValueNodes.size)
        composeTestRule.onNodeWithText("No expenses logged for this month.").assertExists()
    }
}
