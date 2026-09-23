package com.contextswitcher.android.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import com.contextswitcher.android.BuildConfig
import com.contextswitcher.android.SettingsScreen
import com.contextswitcher.android.settings.RepoSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// The phone says which build it runs, and hands over the sha.
// [utest->dsn~android-running-commit~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildCommitLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the build carries the commit line of its checkout`() {
        assertThat(BuildConfig.GIT_COMMIT_LINE).matches("[0-9a-f]{7,} \\(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}\\)")
        assertThat(BuildConfig.GIT_COMMIT).startsWith(BuildConfig.GIT_COMMIT_LINE.substringBefore(' '))
    }

    @Test
    fun `the settings screen shows the build commit`() {
        composeRule.setContent {
            SettingsScreen(RepoSettings("", "", ""), "", onSaved = { _, _ -> }, onClearKnownHosts = {})
        }

        composeRule.onNodeWithText(BuildConfig.GIT_COMMIT_LINE).assertExists()
    }

    @Test
    fun `a long press copies the bare sha`() {
        composeRule.setContent { BuildCommitLine(commitLine = "06397024 (2026-09-17 08:30)") }

        composeRule.onNodeWithText("06397024 (2026-09-17 08:30)").performTouchInput { longClick() }
        composeRule.waitForIdle()

        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        assertThat(clipboard.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("06397024")
    }
}
