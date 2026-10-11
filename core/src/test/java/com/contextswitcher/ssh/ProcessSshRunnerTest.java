package com.contextswitcher.ssh;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~ssh-command-runner~7]
class ProcessSshRunnerTest {

    /// sshd's default `MaxStartups` refuses the eleventh unauthenticated
    /// connection; the app's own bound stays under that with room for the
    /// terminal mirror and the user's shells.
    @Test
    void concurrentSshProcessesStayUnderSshdsDefaultMaxStartups() {
        assertThat(ProcessSshRunner.MAX_CONCURRENT).isBetween(2, 9);
    }

    @Test
    void argvUsesBatchModeAndKeepsRemoteCommandArgsSeparate() {
        List<String> command = ProcessSshRunner.buildCommand(
                "devbox", List.of("tmux", "select-window", "-t", "jabref:claude"));

        assertThat(command).containsExactly(
                ProcessSshRunner.sshExecutable(), "-n", "-o", "BatchMode=yes", "-o", "ConnectTimeout=5", "devbox",
                "sh", "-c", "'tmux select-window -t jabref:claude'");
    }

    /// The login shell may be fish: the command reaches it as one quoted
    /// word for `sh -c`, single quotes inside escaped the POSIX way, which
    /// fish reads alike; a command that already runs `sh` stays unwrapped.
    @Test
    void remoteCommandRunsUnderShWhateverTheLoginShell() {
        assertThat(ProcessSshRunner.posix(List.of("m=cs-mirror-0;", "tmux", "list-windows",
                "-F", "'#{window_id}'")))
                .containsExactly("sh", "-c", "'m=cs-mirror-0; tmux list-windows -F '\\''#{window_id}'\\'''");
        assertThat(ProcessSshRunner.posix(List.of("sh", "-c", "'echo $HOME'")))
                .containsExactly("sh", "-c", "'echo $HOME'");
    }

    @Test
    void stdinVariantOmitsDashNSoTheRemoteCommandCanReadItsInput() {
        List<String> command = ProcessSshRunner.buildCommand(
                "devbox", List.of("tmux", "load-buffer", "-b", "cs-queue", "-"), true);

        assertThat(command).containsExactly(
                ProcessSshRunner.sshExecutable(), "-o", "BatchMode=yes", "-o", "ConnectTimeout=5", "devbox",
                "sh", "-c", "'tmux load-buffer -b cs-queue -'");
    }

    // PATH-resolved ssh may be an MSYS build (Git for Windows, Cygwin) that
    // re-parses the argv and strips single quotes; on Windows the built-in
    // OpenSSH client is addressed by absolute path instead.
    @Test
    void onWindowsTheInstalledOpenSshClientIsAddressedAbsolutely(@TempDir Path dir) throws Exception {
        Path sshExe = dir.resolve("ssh.exe");
        Files.createFile(sshExe);

        assertThat(ProcessSshRunner.resolveSsh(true, sshExe)).isEqualTo(sshExe.toString());
    }

    @Test
    void withoutTheWindowsClientThePathResolvedSshIsUsed(@TempDir Path dir) {
        Path missing = dir.resolve("ssh.exe");

        assertThat(ProcessSshRunner.resolveSsh(true, missing)).isEqualTo("ssh");
        assertThat(ProcessSshRunner.resolveSsh(false, missing)).isEqualTo("ssh");
    }
}
