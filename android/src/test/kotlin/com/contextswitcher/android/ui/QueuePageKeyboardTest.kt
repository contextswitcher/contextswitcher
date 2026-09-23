package com.contextswitcher.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import kotlin.io.path.createTempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// With the keyboard open, the message box's Send button stays above it —
/// the app is edge-to-edge, so nothing but the page's own padding moves it.
// [utest->dsn~android-queue-send~2]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueuePageKeyboardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val task = Task("alpha/one", "One", TaskStatus.ACTIVE, null, null, null, null, null, null, null,
        emptyList(), emptyList(), emptyList(), null, null, "")

    @Test
    fun `send stays above an open keyboard`() {
        val activity = composeRule.activity
        composeRule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(activity.window, false) }
        composeRule.setContent {
            QueuePage(task, emptyList(), DraftStore(createTempDirectory("drafts")), send = { null },
                snackbarHostState = SnackbarHostState())
        }
        composeRule.onNodeWithText("Message").performTextInput("a message")

        // A third of the window, like a phone keyboard (Robolectric's default screen is small).
        val keyboardPx = activity.window.decorView.height / 3
        composeRule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardPx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
        }
        composeRule.waitForIdle()

        val density = activity.resources.displayMetrics.density
        val rootBottom = composeRule.onRoot().getUnclippedBoundsInRoot().bottom
        val keyboardTop = rootBottom - Dp(keyboardPx / density)
        val sendBottom = composeRule.onNodeWithText("Send").getUnclippedBoundsInRoot().bottom
        assertThat(sendBottom.value).isLessThanOrEqualTo(keyboardTop.value)
    }
}
