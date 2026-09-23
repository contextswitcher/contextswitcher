package com.contextswitcher.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import kotlin.io.path.createTempDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// A tablet in landscape shows the terminal and the queue side by side; a
/// phone keeps the tabbed pager.
// [utest->dsn~android-large-screen~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDetailLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val task = Task("alpha/one", "One", TaskStatus.ACTIVE, null, null, null, null, null, null, null,
        emptyList(), emptyList(), emptyList(), null, null, "")

    private fun show() {
        val dir = createTempDirectory("detail")
        val ssh = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SshCommandRunner::class.java)) { _, _, _ ->
            throw UnsupportedOperationException()
        } as SshCommandRunner
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    TaskDetailScreen(task, dir, DraftStore(dir), ssh, MessageSender(ssh, dir), onBack = {})
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `wide window shows terminal and queue side by side without tabs`() {
        show()
        composeRule.onNodeWithText("No live session for this task").assertExists()
        composeRule.onNodeWithText("Message").assertExists()
        composeRule.onNodeWithText("Queue").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun `phone window keeps the tabs`() {
        show()
        composeRule.onNodeWithText("Terminal").assertExists()
        composeRule.onNodeWithText("Queue").assertExists()
    }
}
