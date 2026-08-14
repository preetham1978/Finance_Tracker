package com.example.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for EditProfileDialog.
 *
 * This pins down the fix for a real gap that WAS present: EditProfileDialog's
 * `LaunchedEffect(user?.uid)` used to only set `isLoading = false` inside the
 * `if (user != null)` branch, with no `else`. That meant if this dialog was ever composed
 * without a signed-in Firebase user - exactly the state Robolectric starts in - it got stuck
 * showing its loading spinner forever, with Save and Cancel permanently disabled and no way
 * out short of tapping outside / pressing back.
 *
 * The fix adds an else branch that stops the spinner and shows an explanatory error message
 * instead. This test proves the dialog is now interactive (Cancel enabled, error shown) even
 * with no signed-in user, rather than pinning down the old stuck-forever behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class EditProfileDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun noSignedInUser_stopsLoadingAndShowsErrorMessage_cancelStaysUsable() {
        composeTestRule.setContent {
            EditProfileDialog(onDismiss = {})
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("You're not signed in. Please sign in and try again.").assertExists()
        composeTestRule.onNodeWithText("Cancel").assertIsEnabled()
    }
}
