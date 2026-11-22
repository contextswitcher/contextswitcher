package com.contextswitcher.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.ssh.SshCommandRunner.SshResult
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// Swiping past the outer pages opens the neighbouring task.
// [utest->dsn~android-task-swipe~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class TaskSwipeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun task(id: String) = Task(id, id, TaskStatus.ACTIVE, null, null, null, null, null, null, null,
        emptyList(), emptyList(), emptyList(), null, null, "")

    private val opened = ArrayList<Pair<String, Boolean>>()

    private fun show(startAtQueue: Boolean) {
        val dir = createTempDirectory("swipe")
        val ssh = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SshCommandRunner::class.java)) { _, _, _ ->
            throw UnsupportedOperationException()
        } as SshCommandRunner
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    TaskDetailScreen(task("b"), dir, DraftStore(dir), ssh, MessageSender(ssh, dir), onBack = {},
                        previousTask = task("a"), nextTask = task("c"), startAtQueue = startAtQueue,
                        onOpenTask = { t, atQueue -> opened += t.id() to atQueue })
                }
            }
        }
    }

    @Test
    fun `swiping left past the queue opens the next task at its terminal`() {
        show(startAtQueue = true)
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(listOf("c" to false), opened)
    }

    @Test
    fun `swiping right past the terminal opens the previous task at its queue`() {
        show(startAtQueue = false)
        composeRule.onNodeWithText("No live session for this task").assertExists()
        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(listOf("a" to true), opened)
    }

    @Test
    fun `the terminal keeps its scroll position across a swipe to the queue and back`() {
        val tmuxTask = Task("b", "b", TaskStatus.ACTIVE, "host", Task.TmuxConfig("0", "@1"), null, null, null, null, null,
            emptyList(), emptyList(), emptyList(), null, null, "")
        val ssh = object : SshCommandRunner {
            override fun run(host: String, remoteCommand: List<String>) =
                if ("capture-pane" in remoteCommand) SshResult(0, (1..200).joinToString("\n") { "line $it" }, "")
                else SshResult(0, "", "")

            override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray) = run(host, remoteCommand)
        }
        val dir = createTempDirectory("swipe")
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    TaskDetailScreen(tmuxTask, dir, DraftStore(dir), ssh, MessageSender(ssh, dir), onBack = {},
                        previousTask = task("a"), nextTask = task("c"))
                }
            }
        }
        val terminal = hasScrollAction() and hasText("line 200", substring = true)
        fun position() = composeRule.onNode(terminal).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(terminal).fetchSemanticsNodes().isNotEmpty() && position() > 0f }
        composeRule.onNode(terminal).performTouchInput { repeat(20) { swipeDown() } }
        composeRule.waitForIdle()
        assertEquals(0f, position())

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(terminal).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(500) // a re-created page's first capture comes back from Dispatchers.IO
        composeRule.waitForIdle()

        assertEquals(0f, position())
    }
}
