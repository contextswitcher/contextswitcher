package com.contextswitcher.android

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.contextswitcher.android.settings.RepoSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// Android kills a backgrounded app while the user fetches a token from a
/// password manager; typed-but-unsaved settings must survive that recreation.
// [utest->dsn~android-task-list~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `unsaved input survives activity recreation`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            SettingsScreen(RepoSettings("", "", ""), "", onSaved = { _, _ -> }, onClearKnownHosts = {})
        }

        composeRule.onNodeWithText("Repo URL").performTextInput("https://example.org/tasks.git")
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("https://example.org/tasks.git").assertExists()
    }

    @Test
    fun `system back cancels the edit`() {
        var cancelled = false
        composeRule.setContent {
            SettingsScreen(RepoSettings("", "", ""), "", onSaved = { _, _ -> }, onClearKnownHosts = {}, onCancel = { cancelled = true })
        }

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        assertTrue(cancelled)
    }

    @Test
    @Config(qualifiers = "w320dp-h400dp")
    fun `save button stays reachable on a small screen with a pasted key`() {
        val key = (1..50).joinToString("\n") { "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW" }
        composeRule.setContent {
            SettingsScreen(RepoSettings("", "", ""), key, onSaved = { _, _ -> }, onClearKnownHosts = {})
        }

        composeRule.onNodeWithText("Save + Sync now").performScrollTo().assertIsDisplayed()
    }
}
