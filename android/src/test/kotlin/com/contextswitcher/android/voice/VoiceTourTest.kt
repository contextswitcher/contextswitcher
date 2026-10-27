package com.contextswitcher.android.voice

import com.contextswitcher.android.car.CarTask
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/// The overview names the waiting tasks, the card reads Claude's last
/// answer from the transcript, and a dictated reply carries the spoken hint.
// [utest->dsn~android-voice-tour~1]
class VoiceTourTest {

    private val ssh = object : SshCommandRunner {
        val commands = ArrayList<String>()
        override fun run(host: String, remoteCommand: List<String>): SshCommandRunner.SshResult {
            commands += host + ": " + remoteCommand.joinToString(" ")
            return SshCommandRunner.SshResult(0,
                """{"type":"assistant","message":{"content":[{"type":"text","text":"Tests pass."}]}}""" + "\n", "")
        }

        override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray) = run(host, remoteCommand)
    }

    private fun task(id: String, title: String, session: String? = "s-$id", pr: String? = null) = Task(id, title, TaskStatus.ACTIVE, "devbox",
        Task.TmuxConfig("jabref", "@1"), null, null, pr?.let { Task.BrowserConfig(listOf(Task.UrlEntry(it, null))) }, Task.ClaudeConfig("/home/o/jabref", session, null),
        null, emptyList(), emptyList(), emptyList(), null, null, "")

    private val tour = VoiceTour("jabref", listOf(
        CarTask(task("a", "Fix the NPE"), "waiting"),
        CarTask(task("b", "Bump Gradle"), "working"),
        CarTask(task("c", "Docs typo", session = null), null),
    ), ssh)

    @Test
    fun `the overview counts and names what waits`() {
        assertThat(tour.overview()).isEqualTo("jabref: 3 tasks. Waiting for you: Fix the NPE. Working: 1.")
        assertThat(VoiceTour("jabref", emptyList(), ssh).overview()).isEqualTo("jabref: no task with a Claude session.")
    }

    @Test
    fun `the card reads the last answer from the task's transcript`() {
        assertThat(tour.announcement()).isEqualTo("Task 1 of 3: Fix the NPE. Status: waiting. Claude said: Tests pass.")
        assertThat(ssh.commands).containsExactly(
            "devbox: grep -F '\"type\":\"assistant\"' ~/.claude/projects/-home-o-jabref/s-a.jsonl | tail -5")
    }

    @Test
    fun `stepping stays within the list and a task without a session has no answer`() {
        assertThat(tour.next()).isTrue()
        assertThat(tour.next()).isTrue()
        assertThat(tour.next()).isFalse()
        assertThat(tour.announcement()).isEqualTo("Task 3 of 3: Docs typo. Status: unknown. No answer from Claude yet.")
        assertThat(tour.previous()).isTrue()
        assertThat(tour.index).isEqualTo(1)
    }

    @Test
    fun cardNamesThePr() {
        val withPr = VoiceTour("jabref", listOf(CarTask(task("a", "Fix the NPE", pr = "https://github.com/JabRef/jabref/pull/16245"), "waiting")), ssh)
        assertThat(withPr.announcement()).startsWith("Task 1 of 1, PR 16245: Fix the NPE.")
        val inTitle = VoiceTour("jabref", listOf(CarTask(task("a", "PR 16245: Fix the NPE", pr = "https://github.com/JabRef/jabref/pull/16245"), "waiting")), ssh)
        assertThat(inTitle.announcement()).startsWith("Task 1 of 1: PR 16245: Fix the NPE.")
    }

    @Test
    fun `a dictated reply asks for a spoken answer`() {
        assertThat(VoiceTour.spokenReply("merge it")).startsWith("(Voice mode:").endsWith(") merge it")
    }
}

/// The call steers on exact command words in English or German and sends everything else;
/// the speaker picks the voice by the text's language.
// [utest->dsn~android-voice-tour~1]
class TourCallTest {

    @Test
    fun commands() {
        assertThat(TourCall.command("Next.")).isEqualTo(TourCall.Command.NEXT)
        assertThat(TourCall.command("weiter")).isEqualTo(TourCall.Command.NEXT)
        assertThat(TourCall.command("Zurück!")).isEqualTo(TourCall.Command.PREVIOUS)
        assertThat(TourCall.command("hang up")).isEqualTo(TourCall.Command.HANG_UP)
        assertThat(TourCall.command("next, please also fix the tests")).isNull()
    }

    @Test
    fun voiceByLanguage() {
        assertThat(Speaker.localeOf("Task 1 of 3: Fix the build. Status: waiting. Claude said: The tests pass and the branch is pushed.")).isEqualTo(java.util.Locale.ENGLISH)
        assertThat(Speaker.localeOf("Task 1 of 3: Build fix. Status: waiting. Claude said: Die Tests laufen durch und der Branch ist gepusht, ich habe nichts weiter geändert.")).isEqualTo(java.util.Locale.GERMAN)
    }
}
