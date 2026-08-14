package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.components.LoginScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Additional LoginScreen coverage focused on client-side validation.
 *
 * All of these enter input that fails validation *before* LoginScreen's onClick handler
 * reaches the Firebase call (see the early `return@Button` checks in LoginScreen.kt), so
 * these tests never touch the network and are safe to run offline/in CI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    // Robolectric's default virtual screen (320x470px) is far smaller than any real phone and
    // was clipping the Sign Up form's Button to zero height (no scroll fallback on LoginScreen's
    // outer Column), which made clicks on it silently miss. A realistic screen size fixes that.
    qualifiers = "w411dp-h891dp",
    instrumentedPackages = ["androidx.loader.content"]
)
class LoginValidationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun blankEmail_showsValidationError_withoutCallingFirebase() {
        composeTestRule.setContent {
            LoginScreen(onLoginSuccess = { throw AssertionError("onLoginSuccess should not fire") })
        }

        composeTestRule.onNodeWithText("Login").performClick()

        composeTestRule.onNodeWithText("Please enter an email address.").assertExists()
    }

    @Test
    fun malformedEmail_showsFormatError() {
        composeTestRule.setContent {
            LoginScreen(onLoginSuccess = { throw AssertionError("onLoginSuccess should not fire") })
        }

        composeTestRule.onNodeWithText("Email Address").performTextInput("not-an-email")
        composeTestRule.onNodeWithText("Password").performTextInput("somepassword")
        composeTestRule.onNodeWithText("Login").performClick()

        composeTestRule.onNodeWithText(
            "Please enter a valid email address format (e.g. user@example.com)."
        ).assertExists()
    }

    @Test
    fun shortPassword_showsMinimumLengthError() {
        composeTestRule.setContent {
            LoginScreen(onLoginSuccess = { throw AssertionError("onLoginSuccess should not fire") })
        }

        composeTestRule.onNodeWithText("Email Address").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("123")
        composeTestRule.onNodeWithText("Login").performClick()

        composeTestRule.onNodeWithText("Password must be at least 6 characters long.").assertExists()
    }

    @Test
    fun signUpToggle_showsNameAndMobileFields() {
        composeTestRule.setContent {
            LoginScreen(onLoginSuccess = {})
        }

        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()

        composeTestRule.onNodeWithText("Create Account").assertExists()
        composeTestRule.onNodeWithText("Name").assertExists()
        composeTestRule.onNodeWithText("Mobile").assertExists()
        composeTestRule.onNodeWithText("Sign Up").assertExists()
    }

    @Test
    fun signUp_blankName_showsValidationError() {
        composeTestRule.setContent {
            LoginScreen(onLoginSuccess = { throw AssertionError("onLoginSuccess should not fire") })
        }

        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        composeTestRule.onNodeWithText("Email Address").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")
        composeTestRule.onNodeWithText("Sign Up").performClick()

        composeTestRule.onNodeWithText("Please enter your name.").assertExists()
    }

    @Test
    fun signUp_invalidMobileNumber_showsValidationError() {
        composeTestRule.setContent {
            LoginScreen(onLoginSuccess = { throw AssertionError("onLoginSuccess should not fire") })
        }

        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        composeTestRule.onNodeWithText("Name").performTextInput("Test User")
        composeTestRule.onNodeWithText("Mobile").performTextInput("abc")
        composeTestRule.onNodeWithText("Email Address").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")
        composeTestRule.onNodeWithText("Sign Up").performClick()

        composeTestRule.onNodeWithText("Please enter a valid mobile number.").assertExists()
    }

    @Test
    fun forgotPassword_blankEmail_showsValidationError() {
        composeTestRule.setContent {
            LoginScreen(onLoginSuccess = {})
        }

        composeTestRule.onNodeWithText("Forgot Password?").performClick()

        composeTestRule.onNodeWithText("Please enter a valid email address to reset password.").assertExists()
    }
}
