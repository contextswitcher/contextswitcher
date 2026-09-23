package com.contextswitcher.queue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~message-queue-send~5]
class MessageSenderTest {

    @TempDir
    Path attachments;

    /// Answers `pwd` with a home, fails every upload whose command names
    /// `failing`, and records the pasted message and which runner ran what.
    private static final class FakeSsh implements SshCommandRunner {
        final List<String> calls = new ArrayList<>();
        final String name;
        final String failing;
        String pasted = "";

        FakeSsh(String name, String failing) {
            this.name = name;
            this.failing = failing;
        }

        @Override
        public SshResult run(String host, List<String> command) {
            calls.add(name + ":" + command.getFirst());
            return new SshResult(0, command.equals(List.of("pwd")) ? "/home/u\n" : "", "");
        }

        @Override
        public SshResult runWithInput(String host, List<String> command, byte[] input) {
            calls.add(name + ":" + command.getFirst());
            if (command.getFirst().equals("tmux")) {
                pasted = new String(input, StandardCharsets.UTF_8);
            } else if (!failing.isEmpty() && String.join(" ", command).contains(failing)) {
                return new SshResult(-1, "", "Timeout after PT5M");
            }
            return new SshResult(0, "", "");
        }
    }

    private Path attachment(String name, int size) throws IOException {
        return Files.write(attachments.resolve(name), new byte[size]);
    }

    @Test
    void uploadsTravelOnTheirOwnRunner() throws IOException {
        FakeSsh ssh = new FakeSsh("ssh", "");
        FakeSsh uploads = new FakeSsh("uploads", "");
        Path jar = attachment("a.jar", 10);
        List<String> progress = new ArrayList<>();

        String error = new MessageSender(ssh, uploads, attachments)
                .send("host", "@1", "see " + Attachments.fileMarker(jar), ClaudeMode.DEFAULT,
                        progress::add);

        assertThat(error).isNull();
        assertThat(uploads.calls).containsExactly("uploads:mkdir");
        assertThat(ssh.calls).doesNotContain("ssh:mkdir");
        assertThat(ssh.pasted).isEqualTo("see `/home/u/.contextswitcher/attachments/a.jar`");
        assertThat(progress).containsExactly("Uploading 1/1: a.jar …", "Sent with 1 attachment.");
    }

    @Test
    void failedUploadDoesNotStopTheOthersAndLeavesNoMarker() throws IOException {
        FakeSsh ssh = new FakeSsh("ssh", "b.jar");
        Path a = attachment("a.jar", 10);
        Path b = attachment("b.jar", 10);
        Path c = attachment("c.jar", 10);
        List<String> progress = new ArrayList<>();
        String text = Attachments.fileMarker(a) + " " + Attachments.fileMarker(b) + " "
                + Attachments.fileMarker(c);

        String error = new MessageSender(ssh, attachments)
                .send("host", "@1", text, ClaudeMode.DEFAULT, progress::add);

        assertThat(error).isNull();
        assertThat(ssh.pasted)
                .contains("`/home/u/.contextswitcher/attachments/a.jar`",
                        "b.jar (not attached)", "`/home/u/.contextswitcher/attachments/c.jar`")
                .doesNotContain(attachments.toString());
        assertThat(progress).contains("Uploading 3/3: c.jar …");
        assertThat(progress.getLast())
                .startsWith("Sent without 1 of 3 attachments (kept in " + attachments)
                .endsWith("b.jar: Timeout after PT5M");
    }

    @Test
    void oversizedFileIsRefusedWithAnAlternative() throws IOException {
        FakeSsh ssh = new FakeSsh("ssh", "");
        Path big = attachments.resolve("big.jar");
        try (RandomAccessFile file = new RandomAccessFile(big.toFile(), "rw")) {
            file.setLength(MessageSender.MAX_UPLOAD_BYTES + 1);
        }
        List<String> progress = new ArrayList<>();

        String error = new MessageSender(ssh, attachments).send("host", "@1",
                "here " + Attachments.fileMarker(big), ClaudeMode.DEFAULT, progress::add);

        assertThat(error).isNull();
        assertThat(ssh.calls).doesNotContain("ssh:mkdir");
        assertThat(ssh.pasted).isEqualTo("here big.jar (not attached)");
        assertThat(progress.getLast()).contains("over the 64 MB limit", "gh release download");
    }

    @Test
    void messageOfOnlyFailedAttachmentsIsNotSent() throws IOException {
        FakeSsh ssh = new FakeSsh("ssh", "a.jar");
        Path a = attachment("a.jar", 10);

        String error = new MessageSender(ssh, attachments)
                .send("host", "@1", " " + Attachments.fileMarker(a) + "\n", ClaudeMode.DEFAULT,
                        line -> {});

        assertThat(error).startsWith("Not sent, no attachment arrived").contains("a.jar: Timeout");
        assertThat(ssh.pasted).isEmpty();
    }
}
