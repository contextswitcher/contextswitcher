package com.contextswitcher.android.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.contextswitcher.discovery.ClaudeUpdateRestart
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.ssh.SshCommandRunner.SshResult
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// The terminal page sits at the end of the screen, like a terminal, and does
/// not pull a reader who scrolled up back down on the next refresh.
// [utest->dsn~android-terminal-snapshot~3]
// [utest->dsn~android-terminal-interrupt~1]
// [utest->dsn~android-claude-restart~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val task = Task("alpha/one", "One", TaskStatus.ACTIVE, null, null, null, null, null, null, null,
        emptyList(), emptyList(), emptyList(), null, null, "")

    /// Answers every capture with `lines` numbered lines plus the blank lines
    /// a pane taller than its content captures below them.
    private class Screen(var lines: Int, var footer: String = "", val history: Int = 0) : SshCommandRunner {
        @Volatile var captures = 0
        val captureCommands = java.util.concurrent.CopyOnWriteArrayList<List<String>>()
        val keys = java.util.concurrent.CopyOnWriteArrayList<List<String>>()

        override fun run(host: String, remoteCommand: List<String>): SshResult {
            if (remoteCommand.any { it.contains("#{@cs_status}") }) {
                keys.add(remoteCommand)
                return SshResult(0, "working|abc|claude\n", "")
            }
            if ("send-keys" in remoteCommand) {
                keys.add(remoteCommand)
                return SshResult(0, "", "")
            }
            captures++
            captureCommands.add(remoteCommand)
            val requested = remoteCommand.getOrNull(remoteCommand.indexOf("-S") + 1)?.takeIf { "-S" in remoteCommand }?.removePrefix("-")?.toInt() ?: 0
            val older = minOf(requested, history)
            val scrollback = ((history - older + 1)..history).joinToString("") { "old $it\n" }
            return SshResult(0, scrollback + (1..lines).joinToString("\n") { "line $it" } + "\n" + footer + "\n\n\n", "")
        }

        override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray): SshResult =
            run(host, remoteCommand)
    }

    private fun show(screen: Screen, scroll: ScrollState) {
        composeRule.setContent {
            TerminalPage(task, Task.TmuxConfig("0", "@1"), "koppor@example.org", screen, SnackbarHostState(),
                scrollState = scroll)
        }
        composeRule.waitUntil(5_000) { screen.captures >= 1 && scroll.maxValue in 1 until Int.MAX_VALUE }
        composeRule.waitForIdle()
    }

    @Test
    fun `opens at the end of the screen, without the blank lines below the output`() {
        val scroll = ScrollState(0)
        show(Screen(200), scroll)

        assertThat(scroll.value).isEqualTo(scroll.maxValue)
        composeRule.onNodeWithText("line 200", substring = true).assertExists()
        composeRule.onNodeWithText("line 200\n", substring = true).assertDoesNotExist()
    }

    @Test
    fun `follows new output at the end`() {
        val scroll = ScrollState(0)
        val screen = Screen(200)
        show(screen, scroll)
        val before = scroll.maxValue

        screen.lines = 260
        composeRule.onNodeWithText("Refresh").performClick()
        composeRule.waitUntil(5_000) { scroll.maxValue > before }
        composeRule.waitForIdle()

        assertThat(scroll.value).isEqualTo(scroll.maxValue)
    }

    @Test
    fun `a reader who scrolled up stays there across a refresh`() {
        val scroll = ScrollState(0)
        val screen = Screen(200)
        show(screen, scroll)
        runBlocking { composeRule.awaitIdle(); scroll.scrollTo(0) }
        composeRule.waitForIdle()
        val before = scroll.maxValue

        screen.lines = 260
        composeRule.onNodeWithText("Refresh").performClick()
        composeRule.waitUntil(5_000) { scroll.maxValue > before }
        composeRule.waitForIdle()

        assertThat(scroll.value).isEqualTo(0)
    }

    @Test
    fun `Ctrl+C reaches the task's window, and the screen is captured again`() {
        val scroll = ScrollState(0)
        val screen = Screen(20)
        show(screen, scroll)
        val capturesBefore = screen.captures

        composeRule.onNodeWithText("Ctrl+C").performClick()
        composeRule.waitUntil(5_000) { screen.keys.isNotEmpty() && screen.captures > capturesBefore }

        assertThat(screen.keys).containsExactly(
            listOf("tmux", "copy-mode", "-q", "-t", "'@1'", "\\;", "send-keys", "-t", "'@1'", "C-c"))
    }

    // [utest->dsn~android-terminal-keys~1]
    @Test
    fun `Esc, not Ctrl+C, is the last key`() {
        assertThat(TERMINAL_KEYS.takeLast(2).map { it.first }).containsExactly("Ctrl+C", "Esc")
    }

    // [utest->dsn~android-terminal-keys~1]
    @Test
    fun `the key buttons answer a checklist in the task's window`() {
        val screen = Screen(20)
        show(screen, ScrollState(0))

        for (label in listOf("↓", "Space", "Enter")) {
            val before = screen.keys.size
            composeRule.onNodeWithText(label).performClick()
            composeRule.waitUntil(5_000) { screen.keys.size > before }
            composeRule.waitForIdle()
        }

        assertThat(screen.keys.map { it.last() }).containsExactly("Down", "Space", "Enter")
        assertThat(screen.keys).allMatch { it.subList(0, 9) == listOf("tmux", "copy-mode", "-q", "-t", "'@1'", "\\;", "send-keys", "-t", "'@1'") }
    }

    @Test
    fun `Restart Claude shows only while the footer asks, and refuses a busy session`() {
        val scroll = ScrollState(0)
        val screen = Screen(200)
        show(screen, scroll)
        composeRule.onNodeWithText("Restart Claude").assertDoesNotExist()

        screen.footer = "  Update installed \u00B7 Restart to update"
        composeRule.onNodeWithText("Refresh").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Restart Claude").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithText("Restart Claude").performClick()
        composeRule.waitUntil(5_000) { screen.keys.any { command -> command.any { it.contains("#{@cs_status}") } } }
        composeRule.waitForIdle()

        assertThat(screen.keys.flatten()).noneMatch { it.contains("/exit") }
    }

    @Test
    fun `each outcome of a restart reads as a sentence`() {
        assertThat(restartMessage(ClaudeUpdateRestart.Outcome.NotIdle("working"))).contains("Claude is working")
        assertThat(restartMessage(ClaudeUpdateRestart.Outcome.Restarted("abc"))).isEqualTo("Claude restarted on the new version")
        assertThat(restartMessage(ClaudeUpdateRestart.Outcome.DidNotQuit())).contains("not resumed")
    }

    @Test
    fun `reaching the top loads earlier output and keeps the reading position`() {
        val scroll = ScrollState(0)
        val screen = Screen(200, history = 1000)
        show(screen, scroll)
        runBlocking { composeRule.awaitIdle(); scroll.scrollTo(0) }
        val shortMax = scroll.maxValue

        composeRule.waitUntil(5_000) { screen.captureCommands.any { "-500" in it } && scroll.maxValue > shortMax }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("old 501", substring = true).assertExists()
        composeRule.onNodeWithText("old 500\n", substring = true).assertDoesNotExist()
        // Still looking at what was the top before: "line 1", now below 500 older lines.
        assertThat(scroll.value).isEqualTo(scroll.maxValue - shortMax)
    }

    @Test
    fun `a window without older output stops offering Earlier`() {
        val scroll = ScrollState(0)
        val screen = Screen(200, history = 0)
        show(screen, scroll)

        composeRule.onNodeWithText("Earlier").performClick()
        composeRule.waitUntil(5_000) { screen.captureCommands.any { "-500" in it } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Earlier").assertIsNotEnabled()
    }

    @Test
    fun `back at the end the capture is a screenful again`() {
        val scroll = ScrollState(0)
        val screen = Screen(200, history = 1000)
        show(screen, scroll)
        composeRule.onNodeWithText("Earlier").performClick()
        composeRule.waitUntil(5_000) { screen.captureCommands.any { "-500" in it } }
        composeRule.waitForIdle()

        runBlocking { scroll.scrollTo(scroll.maxValue) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Refresh").performClick()
        val before = screen.captureCommands.size
        composeRule.waitUntil(5_000) { screen.captureCommands.size > before }

        assertThat(screen.captureCommands.last()).doesNotContain("-S")
    }
}
