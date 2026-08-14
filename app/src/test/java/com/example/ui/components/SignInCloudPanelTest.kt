package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for SignInCloudPanel - the Gmail sign-in form inside GoogleCloudDialog.
 *
 * GoogleCloudDialog itself takes a live FinanceViewModel and can't be safely instantiated in
 * Robolectric (same Firebase/Room coupling issue as CategoryManagementTab / CardsLoansTab /
 * ScannerTab). SignInCloudPanel, though, is a separate top-level composable in the same file
 * that only takes plain callbacks - so its name/email/password validation logic is testable
 * in isolation, independent of the ViewModel-backed dialog around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SignInCloudPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private data class SignInCall(val email: String, val name: String, val password: String)

    @Test
    fun submittingBlankForm_showsAllValidationErrors_andDoesNotCallOnSignIn() {
        val calls = mutableListOf<SignInCall>()
        composeTestRule.setContent {
            SignInCloudPanel(
                statusMsg = null,
                onClearMsg = {},
                onSignIn = { email, name, password -> calls.add(SignInCall(email, name, password)) }
            )
        }

        composeTestRule.onNodeWithTag("google_signin_submit_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Name must be at least 2 characters long").assertExists()
        composeTestRule.onNodeWithText("Please enter a valid Gmail address").assertExists()
        composeTestRule.onNodeWithText("Password must be at least 6 characters long").assertExists()
        assertEquals(0, calls.size)
    }

    @Test
    fun validInputs_callOnSignInWithTrimmedNameAndEmail() {
        val calls = mutableListOf<SignInCall>()
        composeTestRule.setContent {
            SignInCloudPanel(
                statusMsg = null,
                onClearMsg = {},
                onSignIn = { email, name, password -> calls.add(SignInCall(email, name, password)) }
            )
        }

        // Deliberately include leading/trailing whitespace to prove the panel trims before
        // calling back, same contract as the manual sign-in flow.
        composeTestRule.onNodeWithTag("auth_name_input").performTextInput("  Preetham Prasad  ")
        composeTestRule.onNodeWithTag("auth_email_input").performTextInput("  preetham@gmail.com  ")
        composeTestRule.onNodeWithTag("auth_password_input").performTextInput("secure123")

        composeTestRule.onNodeWithTag("google_signin_submit_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, calls.size)
        assertEquals("preetham@gmail.com", calls.first().email)
        assertEquals("Preetham Prasad", calls.first().name)
        assertEquals("secure123", calls.first().password)
    }

    @Test
    fun invalidEmailFormat_isRejected_evenWithNameAndPasswordValid() {
        val calls = mutableListOf<SignInCall>()
        composeTestRule.setContent {
            SignInCloudPanel(
                statusMsg = null,
                onClearMsg = {},
                onSignIn = { email, name, password -> calls.add(SignInCall(email, name, password)) }
            )
        }

        composeTestRule.onNodeWithTag("auth_name_input").performTextInput("Preetham Prasad")
        composeTestRule.onNodeWithTag("auth_email_input").performTextInput("not-an-email")
        composeTestRule.onNodeWithTag("auth_password_input").performTextInput("secure123")

        composeTestRule.onNodeWithTag("google_signin_submit_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Please enter a valid Gmail address").assertExists()
        assertEquals(0, calls.size)
    }
}
