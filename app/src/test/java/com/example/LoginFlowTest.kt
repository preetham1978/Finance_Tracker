package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.LoginScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "w411dp-h891dp",
    instrumentedPackages = ["androidx.loader.content"]
)
class LoginFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLoginScreenUIElements() {
        composeTestRule.setContent {
            LoginScreen(onLoginSuccess = {})
        }

        // Check if primary elements are present
        composeTestRule.onNodeWithText("Vantage Finance Login").assertExists()
        composeTestRule.onNodeWithText("Email Address").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()
        composeTestRule.onNodeWithText("Login").assertExists()
    }
}
