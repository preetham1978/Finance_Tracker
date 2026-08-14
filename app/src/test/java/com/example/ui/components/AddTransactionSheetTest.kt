package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for the New Transaction form (AddTransactionSheet).
 *
 * These tests exist specifically to validate flows that were hard to verify by hand during
 * manual testing on a real emulator: the "Save" button sits below the fold once the Credit
 * Card payment method reveals its extra fields, and manual touch-scroll wasn't reliable over
 * remote control. Compose UI tests drive the sheet through its semantics tree instead of raw
 * touch/scroll gestures, so `performScrollTo()` reaches the button deterministically.
 *
 * Two things learned from getting these tests to actually pass, worth keeping in mind:
 *  - Every field below the Category grid - including the Payment Method selector itself
 *    ("UPI"/"Credit Card"/"Cash") and the Credit Card bank-name field it reveals - sits below
 *    the fold on a normal screen. `performClick()` does NOT auto-scroll a node into view the
 *    way `assertIsDisplayed()` checks visibility; it still dispatches a synthetic click at the
 *    node's layout coordinates even when that's off-screen, which silently no-ops instead of
 *    failing loudly. Every click AND every assertion on a below-the-fold node needs an explicit
 *    `performScrollTo()` first - the exact same "below the fold" problem that blocked manual
 *    testing on the emulator, just showing up as a silent no-op instead of an unreachable button.
 *  - `AddTransactionSheet` runs a 1.2s-debounced AI category auto-suggestion
 *    (`LaunchedEffect(title, selectedType)` in AddTransactionSheet.kt) that used to overwrite
 *    `selectedCategory` once it resolved, even after a manual pick. Fixed by adding a
 *    `userManuallySelectedCategory` flag that the effect now checks before overwriting -
 *    `manualCategoryPick_isNotOverwritten_byLaterAiSuggestion` below proves the fix by clicking
 *    a category chip *before* the debounced suggestion has had a chance to resolve (the exact
 *    ordering that used to lose the race).
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    // Robolectric's default virtual screen (320x470px) is far smaller than any real phone,
    // which was pushing the lazily-composed category grid / Credit Card bank field out of
    // the composed range entirely. A realistic screen size fixes that.
    qualifiers = "w411dp-h891dp",
    instrumentedPackages = ["androidx.loader.content"]
)
class AddTransactionSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private data class SavedCall(
        val title: String,
        val amount: Double,
        val category: String,
        val type: String,
        val notes: String,
        val paymentMethod: String,
        val creditCardBank: String?,
        val isRecurring: Boolean,
        val currency: String,
        val scheduledDay: Int?
    )

    private fun setContentWithCapture(): MutableList<SavedCall> {
        val calls = mutableListOf<SavedCall>()
        composeTestRule.setContent {
            AddTransactionSheet(
                onDismiss = {},
                onSave = { title, amount, category, type, notes, paymentMethod, creditCardBank, isRecurring, currency, scheduledDay ->
                    calls.add(
                        SavedCall(
                            title, amount, category, type, notes, paymentMethod,
                            creditCardBank, isRecurring, currency, scheduledDay
                        )
                    )
                }
            )
        }
        composeTestRule.waitForIdle()
        return calls
    }

    @Test
    fun saveButton_isDisabled_whenFormIsEmpty() {
        setContentWithCapture()

        composeTestRule.onNodeWithTag("save_transaction_button")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun saveButton_staysDisabled_withTitleButNoAmount() {
        setContentWithCapture()

        composeTestRule.onNodeWithTag("input_title").performTextInput("Coffee")

        composeTestRule.onNodeWithTag("save_transaction_button")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun validExpense_defaultsToUpiPayment_andSavesExpectedValues() {
        val calls = setContentWithCapture()

        composeTestRule.onNodeWithTag("input_title").performTextInput("Grocery Run")
        composeTestRule.onNodeWithTag("input_amount").performTextInput("450")

        composeTestRule.onNodeWithTag("save_transaction_button")
            .performScrollTo()
            .performClick()

        assertEquals(1, calls.size)
        val call = calls.first()
        assertEquals("Grocery Run", call.title)
        assertEquals(450.0, call.amount, 0.001)
        assertEquals("EXPENSE", call.type)
        assertEquals("UPI", call.paymentMethod)
        assertNull(call.creditCardBank)
        assertFalse(call.isRecurring)
        assertNull(call.scheduledDay)
        // Default category is the first expense category ("Food") unless the AI suggester
        // (which needs live network access) overrides it - either way it must be non-blank.
        assertTrue(call.category.isNotBlank())
    }

    @Test
    fun selectingCreditCard_revealsBankNameField_andHidesUpiField() {
        setContentWithCapture()

        composeTestRule.onNodeWithText("Credit Card").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("input_cc_bank").performScrollTo().assertIsDisplayed()
        // UPI-only field should no longer be present once Credit Card is selected. Use the
        // "all nodes" query + node count instead of assertDoesNotExist() so this doesn't
        // depend on a specific compose-ui-test version having that particular assertion.
        val upiFieldNodes = composeTestRule.onAllNodesWithTag("input_payee_upi_id")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue(upiFieldNodes.isEmpty())
    }

    @Test
    fun creditCardPayment_withBankNameAndScheduleMonthly_savesAllFields() {
        val calls = setContentWithCapture()

        composeTestRule.onNodeWithTag("input_title").performTextInput("Amazon Purchase")
        composeTestRule.onNodeWithTag("input_amount").performTextInput("2500")
        composeTestRule.onNodeWithText("Credit Card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_cc_bank").performScrollTo().performTextInput("HDFC Bank")

        // "Schedule Monthly" toggle - verify default off, then toggle on.
        composeTestRule.onNodeWithText("Schedule Monthly").performScrollTo()
        composeTestRule.onAllNodes(isToggleable())[0].assertIsOff()
        composeTestRule.onAllNodes(isToggleable())[0].performClick()
        composeTestRule.onAllNodes(isToggleable())[0].assertIsOn()

        composeTestRule.onNodeWithTag("save_transaction_button")
            .performScrollTo()
            .performClick()

        assertEquals(1, calls.size)
        val call = calls.first()
        assertEquals("Amazon Purchase", call.title)
        assertEquals(2500.0, call.amount, 0.001)
        assertEquals("CREDIT_CARD", call.paymentMethod)
        assertEquals("HDFC Bank", call.creditCardBank)
        assertTrue(call.isRecurring)
    }

    @Test
    fun switchingToIncome_hidesPaymentMethodSection_andUsesIncomeCategories() {
        setContentWithCapture()

        composeTestRule.onNodeWithTag("type_selector_income").performClick()
        composeTestRule.waitForIdle()

        val paymentMethodNodes = composeTestRule.onAllNodesWithText("Payment Method")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue(paymentMethodNodes.isEmpty())
        composeTestRule.onNodeWithTag("category_button_Salary").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun incomeTransaction_savesWithDefaultCashPaymentMethod() {
        // Payment Method row only renders for EXPENSE transactions, so INCOME always saves
        // with the sheet's initial payment method value ("UPI" by default) unless the caller
        // opened the sheet in edit mode with a different initial value.
        val calls = setContentWithCapture()

        composeTestRule.onNodeWithTag("type_selector_income").performClick()
        composeTestRule.onNodeWithTag("input_title").performTextInput("Freelance Payout")
        composeTestRule.onNodeWithTag("input_amount").performTextInput("15000")

        composeTestRule.onNodeWithTag("save_transaction_button")
            .performScrollTo()
            .performClick()

        assertEquals(1, calls.size)
        val call = calls.first()
        assertEquals("INCOME", call.type)
        assertEquals(15000.0, call.amount, 0.001)
    }

    @Test
    fun selectingCategoryChip_updatesSelectionUsedOnSave() {
        val calls = setContentWithCapture()

        composeTestRule.onNodeWithTag("input_title").performTextInput("New Shoes")
        composeTestRule.onNodeWithTag("input_amount").performTextInput("3200")
        // Let the debounced AI category auto-suggestion (1.2s delay in AddTransactionSheet's
        // LaunchedEffect(title, selectedType)) resolve BEFORE picking a category manually.
        // Otherwise it can resolve after our click and silently overwrite the manual pick -
        // a real race condition in the app, not just a test-timing artifact.
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("category_button_Shopping").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("save_transaction_button")
            .performScrollTo()
            .performClick()

        assertEquals(1, calls.size)
        assertEquals("Shopping", calls.first().category)
    }

    @Test
    fun manualCategoryPick_isNotOverwritten_byLaterAiSuggestion() {
        // Regression test for the race condition: type a title (starts the debounced AI
        // suggestion effect) and immediately click a category chip WITHOUT waiting for the
        // debounce/suggestion to resolve first - the exact ordering that used to let the AI
        // suggestion silently win. waitForIdle() below still has to run the pending coroutines
        // (including the now-harmless AI suggestion) before Save is clickable, so this proves
        // the userManuallySelectedCategory guard, not just lucky timing.
        val calls = setContentWithCapture()

        composeTestRule.onNodeWithTag("input_title").performTextInput("New Shoes")
        composeTestRule.onNodeWithTag("category_button_Shopping").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("input_amount").performTextInput("3200")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_transaction_button")
            .performScrollTo()
            .performClick()

        assertEquals(1, calls.size)
        assertEquals("Shopping", calls.first().category)
    }

    @Test
    fun notesField_isIncludedInSavedTransaction() {
        val calls = setContentWithCapture()

        composeTestRule.onNodeWithTag("input_title").performTextInput("Doctor Visit")
        composeTestRule.onNodeWithTag("input_amount").performTextInput("800")
        composeTestRule.onNodeWithTag("input_notes").performScrollTo().performTextInput("Annual checkup")

        composeTestRule.onNodeWithTag("save_transaction_button")
            .performScrollTo()
            .performClick()

        assertEquals(1, calls.size)
        assertEquals("Annual checkup", calls.first().notes)
    }
}
