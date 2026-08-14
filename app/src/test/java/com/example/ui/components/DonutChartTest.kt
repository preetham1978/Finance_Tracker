package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for DonutChart - the Dashboard's expense-distribution visualization.
 *
 * Like BalanceCard, this takes plain data (Map<String, Double>) rather than a ViewModel,
 * so it's directly testable. The actual Canvas arc drawing isn't inspectable via the
 * semantics tree, so these tests focus on the two things that ARE observable: the empty
 * state, and the text legend (category name, formatted amount, percentage) that's
 * generated from the same breakdown data driving the chart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DonutChartTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyBreakdown_showsEmptyStateMessage() {
        composeTestRule.setContent {
            DonutChart(categoryBreakdown = emptyMap())
        }

        composeTestRule.onNodeWithText("No expenses recorded yet").assertExists()
    }

    @Test
    fun populatedBreakdown_showsTotalAndPerCategoryLegendWithPercentages() {
        composeTestRule.setContent {
            DonutChart(
                categoryBreakdown = mapOf(
                    "Food" to 600.0,
                    "Shopping" to 400.0
                ),
                activeCurrency = "INR"
            )
        }
        composeTestRule.waitForIdle()

        // Center label: total of the breakdown.
        composeTestRule.onNodeWithText("₹1,000.00").performScrollTo().assertExists()

        // Legend rows: category name plus "amount (percent%)".
        composeTestRule.onNodeWithText("Food").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("₹600.00 (60.0%)").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Shopping").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("₹400.00 (40.0%)").performScrollTo().assertExists()
    }
}
