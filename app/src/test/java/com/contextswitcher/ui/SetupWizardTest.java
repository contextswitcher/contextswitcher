package com.contextswitcher.ui;

import java.util.List;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.ssh.SshCommandRunner;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The wizard's checks read the home and the tmux and Claude Code versions
/// from one answer; ssh refused, tmux missing or `claude` missing each name
/// what to fix (`dsn~setup-wizard~8`).
// [utest->dsn~setup-wizard~8]
class SetupWizardTest {

    @Test
    void homeAndBothVersionsAreReadFromOneAnswer() {
        SetupWizard.Verdict verdict = SetupWizard.remoteVerdict(
                new SshCommandRunner.SshResult(0, "/home/me\ntmux 3.4\n2.0.5 (Claude Code)\n", ""),
                "devbox");
        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.probe().home()).isEqualTo("/home/me");
        assertThat(verdict.probe().tmuxVersion()).isEqualTo("tmux 3.4");
        assertThat(verdict.probe().claudeVersion()).isEqualTo("2.0.5");
        assertThat(verdict.message()).isEqualTo("OK: tmux 3.4, Claude Code 2.0.5 on devbox");
    }

    @Test
    void aRefusedConnectionShowsSshsReason() {
        SetupWizard.Verdict verdict = SetupWizard.remoteVerdict(new SshCommandRunner.SshResult(255,
                "", "me@host: Permission denied (publickey).\n"), "host");
        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.message()).isEqualTo("Failed: me@host: Permission denied (publickey).");
    }

    @Test
    void aMissingToolIsNamed() {
        SetupWizard.Verdict noClaude = SetupWizard.remoteVerdict(new SshCommandRunner.SshResult(127,
                "/home/me\ntmux 3.4\n", "sh: claude: command not found"), "host");
        assertThat(noClaude.ok()).isFalse();
        assertThat(noClaude.message()).startsWith("Missing on host: Claude Code");
        SetupWizard.Verdict nothing = SetupWizard.remoteVerdict(new SshCommandRunner.SshResult(127,
                "/home/me\n", ""), "host");
        assertThat(nothing.message()).startsWith("Missing on host: tmux and Claude Code");
    }

    @Test
    void windowsNeedsNoTmux() {
        SetupWizard.Verdict verdict = SetupWizard.toolsVerdict("C:\\Users\\me", null,
                "2.0.5 (Claude Code)", false, "on this machine");
        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.message()).isEqualTo("OK: Claude Code 2.0.5 on this machine");
        assertThat(SetupWizard.toolsVerdict("/home/me", null, "2.0.5 (Claude Code)", true,
                "on this machine").message()).startsWith("Missing on this machine: tmux.");
    }

    /// `wsl.exe` prints UTF-16LE, which reaches the runner decoded as UTF-8:
    /// a mangled BOM and a NUL after every ASCII character. The names have to
    /// survive that, and the blank trailing line must not become a choice.
    // [utest->dsn~wsl-sessions~1]
    @Test
    void readsTheDistributionsOutOfWslsUtf16Listing() {
        String listing = "\ufffd\ufffdU\u0000b\u0000u\u0000n\u0000t\u0000u\u0000\r\u0000\n"
                + "\u0000D\u0000e\u0000b\u0000i\u0000a\u0000n\u0000\r\u0000\n\u0000\r\u0000\n\u0000";
        assertThat(SetupWizard.wslDistros(runnerPrinting(0, listing)))
                .containsExactly("Ubuntu", "Debian");
    }

    /// No WSL installed (or not Windows at all): the box stays empty rather
    /// than offering whatever the failure printed.
    // [utest->dsn~wsl-sessions~1]
    @Test
    void noDistributionsWhenWslCannotBeAsked() {
        assertThat(SetupWizard.wslDistros(runnerPrinting(-1, "wsl.exe is Windows-only"))).isEmpty();
    }

    private static LocalCommandRunner runnerPrinting(int exitCode, String stdout) {
        return new LocalCommandRunner() {
            @Override
            public LocalResult run(List<String> command, java.nio.file.@Nullable Path directory,
                    byte @Nullable [] input) {
                return new LocalResult(exitCode, stdout, "");
            }
        };
    }
}
