package com.contextswitcher.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.ssh.SshCommandRunner.SshResult
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// A task synced before the desktop recreated its window still names the old
/// id; opening it finds the window its Claude session runs in now.
// [utest->dsn~android-window-lookup~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StaleWindowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the terminal follows the Claude session into its new window`() {
        val task = Task("work/one", "One", TaskStatus.ACTIVE, "host", Task.TmuxConfig("0", "@857"), null, null, null,
            Task.ClaudeConfig("/w", "sess-1", null), null, emptyList(), emptyList(), emptyList(), null, null, "")
        val commands = CopyOnWriteArrayList<List<String>>()
        val ssh = object : SshCommandRunner {
            override fun run(host: String, remoteCommand: List<String>): SshResult {
                commands.add(remoteCommand)
                return if ("list-windows" in remoteCommand) {
                    SshResult(0, "0|@858|0|/w|bash||||||host||other\n0|@931|1|/w|claude|||sess-1|||host||One\n", "")
                } else {
                    SshResult(0, "", "")
                }
            }

            override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray) = SshResult(0, "", "")
        }
        val dir = createTempDirectory("stale")
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    TaskDetailScreen(task, dir, DraftStore(dir), ssh, MessageSender(ssh, dir), onBack = {})
                }
            }
        }

        composeRule.waitUntil(5_000) { commands.any { command -> command.any { it.contains("@931") } } }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("using @931", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(commands.filter { "list-windows" in it }).hasSize(1)
    }
}
