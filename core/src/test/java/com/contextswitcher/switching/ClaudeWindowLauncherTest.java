package com.contextswitcher.switching;

import com.contextswitcher.queue.ClaudeMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-from-pr~6]
class ClaudeWindowLauncherTest {

    // The start command only brings Claude's TUI up (server-side sleep);
    // the prompt itself travels over stdin into a tmux buffer afterwards,
    // so it never appears in an argv.
    @Test
    void startCommandBringsClaudeUpWithoutCarryingThePrompt() {
        assertThat(ClaudeWindowLauncher.startClaudeCommand("@7", false))
                .containsExactly("tmux",
                        "send-keys", "-t", "'@7'", "'claude'", "Enter", "\\;",
                        "run-shell", "'sleep 5'")
                .noneMatch(part -> part.contains("\""));
    }

    // [utest->dsn~claude-auto-permissions~1]
    @Test
    void autoModeStartsClaudeWithSkippedPermissions() {
        assertThat(ClaudeWindowLauncher.startClaudeCommand("@7", true))
                .contains("'claude --dangerously-skip-permissions'");
    }

    // A forked task resumes the source session and forks it, rather than
    // starting a fresh one.
    // [utest->dsn~task-fork~1]
    @Test
    void forkFromResumesAndForksTheSourceSession() {
        assertThat(ClaudeWindowLauncher.startClaudeCommand("@7", false, "abc-123-def"))
                .containsExactly("tmux",
                        "send-keys", "-t", "'@7'", "'claude --resume abc-123-def --fork-session'",
                        "Enter", "\\;", "run-shell", "'sleep 5'");
    }

    // [utest->dsn~task-fork~1]
    @Test
    void forkFromAndAutoCombine() {
        assertThat(ClaudeWindowLauncher.startClaudeCommand("@7", true, "abc-123-def"))
                .contains("'claude --resume abc-123-def --fork-session --dangerously-skip-permissions'");
    }

    // No forkFrom is the plain fresh-session command, unchanged.
    // [utest->dsn~task-fork~1]
    @Test
    void nullForkFromIsAPlainStart() {
        assertThat(ClaudeWindowLauncher.startClaudeCommand("@7", false, null))
                .isEqualTo(ClaudeWindowLauncher.startClaudeCommand("@7", false));
    }

    // The trust dialog's default is "No, exit": the answer must move to the
    // trust option first, and wait for the TUI that follows.
    // [utest->dsn~claude-trust-dialog~1]
    @Test
    void trustAnswerSelectsTheTrustOptionThenWaits() {
        assertThat(ClaudeWindowLauncher.trustCommand("@7"))
                .containsExactly("tmux", "send-keys", "-t", "'@7'", "Down", "Enter", "\\;",
                        "run-shell", "'sleep 3'");
    }

    // The delivery check looks for the prompt's first real line in the
    // pane; marker-only lines are skipped (they are rewritten on the way),
    // and the probe is short enough to survive Claude's line wrapping.
    @Test
    void probeIsTheFirstMarkerFreeLineCapped() {
        assertThat(ClaudeWindowLauncher.probe("\n[image: a.png]\n  Fix the scrollbar in the file field of the entry editor\nmore"))
                .isEqualTo("Fix the scrollbar in the file");
        assertThat(ClaudeWindowLauncher.probe("[file: spec.md]")).isEmpty();
        assertThat(ClaudeWindowLauncher.capturePaneCommand("@7"))
                .containsExactly("tmux", "capture-pane", "-p", "-J", "-S", "-200", "-t", "'@7'");
    }

    // A half the fresh session already starts with is not sent again: the
    // model per the settings, the effort per the start banner (it also
    // reflects CLAUDE_EFFORT and per-model settings); unreadable input skips nothing.
    // [utest->dsn~claude-mode-select~3]
    @Test
    void modeDropsHalvesTheSessionAlreadyStartsWith() {
        ClaudeMode picked = new ClaudeMode("opus", "medium");
        String banner = "▝▜██████▀  Opus 5 with medium effort · Claude Max";
        assertThat(ClaudeWindowLauncher.withoutConfigured(picked,
                "{\"model\":\"opus\",\"effortLevel\":\"high\"}", banner))
                .isEqualTo(ClaudeMode.DEFAULT);
        assertThat(ClaudeWindowLauncher.withoutConfigured(picked, "{\"model\":\"sonnet\"}", "with high effort"))
                .isEqualTo(picked);
        assertThat(ClaudeWindowLauncher.withoutConfigured(new ClaudeMode("default", null), "{}", ""))
                .isEqualTo(ClaudeMode.DEFAULT);
        assertThat(ClaudeWindowLauncher.withoutConfigured(picked, "not json", banner))
                .isEqualTo(new ClaudeMode("opus", null));
        assertThat(ClaudeWindowLauncher.withoutConfigured(picked, "", "")).isEqualTo(picked);
    }
}
