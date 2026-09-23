package com.contextswitcher.terminal;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-suspend-snapshot~3]
class PaneSnapshotsTest {

    /// Scripted ssh runner: pops the given results, records every argv.
    private static final class FakeSsh implements SshCommandRunner {
        final List<List<String>> calls = new ArrayList<>();
        final List<SshResult> results;

        FakeSsh(SshResult... scripted) {
            this.results = new ArrayList<>(List.of(scripted));
        }

        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            calls.add(remoteCommand);
            return results.removeFirst();
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    }

    private static Task task(String id) {
        return new Task(id, "T", TaskStatus.ACTIVE, "devbox",
                new Task.TmuxConfig("jabref", "@17"), null, null, null, null, null, "");
    }

    @Test
    void capturesTheVisibleScreenWithColor() {
        assertThat(PaneSnapshots.captureCommand(new Task.TmuxConfig("jabref", "@17")))
                .containsExactly("tmux", "capture-pane", "-e", "-p", "-t", "@17");
    }

    @Test
    void taskIdBecomesAFlatFileName() {
        assertThat(PaneSnapshots.fileName("jabref/fix-npe")).isEqualTo("jabref_fix-npe.txt");
    }

    @Test
    void capturedScreenIsReadBack(@TempDir Path dir) {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "› Done\n", ""));
        PaneSnapshots snapshots = new PaneSnapshots(ssh, dir.resolve("snapshots"));

        snapshots.capture(task("jabref/fix-npe"));

        assertThat(ssh.calls).containsExactly(List.of("tmux", "capture-pane", "-e", "-p", "-t", "@17"));
        assertThat(snapshots.read("jabref/fix-npe")).isEqualTo("› Done\n");
    }

    @Test
    void noSnapshotWithoutOne(@TempDir Path dir) {
        assertThat(new PaneSnapshots(new FakeSsh(), dir).read("never-suspended")).isNull();
    }

    @Test
    void failedCaptureKeepsThePreviousSnapshot(@TempDir Path dir) {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "first\n", ""),
                new SshCommandRunner.SshResult(1, "", "can't find window @17"));
        PaneSnapshots snapshots = new PaneSnapshots(ssh, dir);

        snapshots.capture(task("t"));
        snapshots.capture(task("t"));

        assertThat(snapshots.read("t")).isEqualTo("first\n");
    }

    // [utest->dsn~android-terminal-snapshot~3]
    @Test
    void historyLinesReachIntoTheScrollback() {
        assertThat(PaneSnapshots.captureCommand(new Task.TmuxConfig("jabref", "@17"), 500))
                .containsExactly("tmux", "capture-pane", "-e", "-p", "-S", "-500", "-t", "@17");
        assertThat(PaneSnapshots.captureCommand(new Task.TmuxConfig("jabref", "@17"), 0))
                .isEqualTo(PaneSnapshots.captureCommand(new Task.TmuxConfig("jabref", "@17")));
    }
}
