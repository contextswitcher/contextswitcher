package com.contextswitcher.provenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.contextswitcher.queue.MessageSender;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import com.contextswitcher.tasks.TextFiles;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~provenance-record~1]
// [utest->dsn~provenance-repository~1]
class ProvenanceRecorderTest {

    private static final Task TASK = new Task("jabref/fix-npe", "Fix the NPE", TaskStatus.ACTIVE, "me@box",
            new Task.TmuxConfig("main", "@17"), null, null, null, null, null, "");

    /// Pastes succeed; the window reports session, workspace and commit.
    private static final class Remote implements SshCommandRunner {
        final List<List<String>> commands = new ArrayList<>();
        boolean pasteFails;

        @Override
        public SshResult run(String host, List<String> command) {
            commands.add(command);
            if (command.contains("display-message")) {
                return new SshResult(0, "sess-1|/ws/fix-npe|abc123\n", "");
            }
            return pasteFails && command.contains("paste-buffer") ? new SshResult(1, "", "no window") : new SshResult(0, "", "");
        }

        @Override
        public SshResult runWithInput(String host, List<String> command, byte[] input) {
            return new SshResult(0, "", "");
        }
    }

    private static List<Path> records(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void aDeliveredMessageBecomesOneRecordWithTheWindowState(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("provenance");
        Remote remote = new Remote();
        List<String> synced = new ArrayList<>();
        MessageSender sender = new MessageSender(remote, tmp.resolve("att"));
        sender.setDeliveryListener(new ProvenanceRecorder(() -> dir, "Desktop Box", remote,
                (host, target) -> ProvenanceRecorder.taskIn(List.of(TASK), host, target),
                category -> category.equals("jabref") ? "https://github.com/JabRef/jabref" : null,
                Clock.fixed(Instant.parse("2026-09-17T12:34:56.789Z"), ZoneOffset.UTC), () -> synced.add("sync")));

        assertThat(sender.send("me@box", "@17", "Please fix it: \"now\"")).isNull();

        List<Path> files = records(dir);
        assertThat(files).containsExactly(dir.resolve("jabref/fix-npe/20260917T123456.789Z-desktop-box.md"));
        assertThat(TextFiles.read(files.get(0))).isEqualTo("""
                ---
                sentAt: '2026-09-17T12:34:56.789Z'
                sender: desktop-box
                task: jabref/fix-npe
                title: Fix the NPE
                repo: https://github.com/JabRef/jabref
                remote: me@box
                window: '@17'
                sessionId: sess-1
                workspace: /ws/fix-npe
                commitBefore: abc123
                ---

                # Sent

                Please fix it: "now"
                """);
        assertThat(synced).containsExactly("sync");
    }

    @Test
    void nothingIsRecordedForAFailedPasteAnUnknownWindowOrWithoutRepository(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("provenance");
        Remote remote = new Remote();
        MessageSender sender = new MessageSender(remote, tmp.resolve("att"));
        sender.setDeliveryListener(new ProvenanceRecorder(() -> dir, "d", remote,
                (host, target) -> ProvenanceRecorder.taskIn(List.of(TASK), host, target), c -> null, Clock.systemUTC(), () -> { }));
        sender.send("me@box", "@99", "to a window no task names");
        remote.pasteFails = true;
        sender.send("me@box", "@17", "never arrived");
        assertThat(records(dir)).isEmpty();

        remote.pasteFails = false;
        sender.setDeliveryListener(new ProvenanceRecorder(() -> null, "d", remote,
                (host, target) -> TASK, c -> null, Clock.systemUTC(), () -> { }));
        sender.send("me@box", "@17", "no provenance repository");
        assertThat(records(dir)).isEmpty();
    }

    @Test
    void twoRecordsInTheSameMillisecondKeepBothFiles(@TempDir Path tmp) throws IOException {
        ProvenanceRecord record = new ProvenanceRecord(Instant.parse("2026-09-17T00:00:00Z"), "phone", "root-task", "T",
                null, "h", "@1", null, null, null, "x");
        Path first = ProvenanceStore.write(tmp, record);
        Path second = ProvenanceStore.write(tmp, record);
        assertThat(first.getFileName()).hasToString("20260917T000000.000Z-phone.md");
        assertThat(second.getFileName()).hasToString("20260917T000000.000Z-phone-2.md");
        assertThat(first.getParent()).isEqualTo(tmp.resolve("_/root-task"));
    }

    @Test
    void theRepositoryIsNamedInProvenanceYaml(@TempDir Path tasks) throws IOException {
        assertThat(ProvenanceConfig.repository(tasks)).isNull();
        Files.writeString(tasks.resolve("provenance.yaml"), "repo: https://github.com/koppor/contextswitcher-task-log.git\n");
        assertThat(ProvenanceConfig.repository(tasks)).isEqualTo("https://github.com/koppor/contextswitcher-task-log.git");
        Files.writeString(tasks.resolve("provenance.yaml"), "repo:\n");
        assertThat(ProvenanceConfig.repository(tasks)).isNull();
        Files.writeString(tasks.resolve("provenance.yaml"), "[not a map");
        assertThat(ProvenanceConfig.repository(tasks)).isNull();
    }
}
