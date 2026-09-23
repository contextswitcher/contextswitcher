package com.contextswitcher.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// [utest->dsn~claude-mode-select~3]
class ClaudeModeTest {

    @Test
    void defaultSendsNothing() {
        assertThat(ClaudeMode.DEFAULT.commands()).isEmpty();
    }

    @Test
    void modelAndEffortBecomeSlashCommands() {
        assertThat(new ClaudeMode("opus[1m]", "high").commands())
                .containsExactly("/model opus[1m]", "/effort high");
    }

    @Test
    void onlyThePickedHalfIsSent() {
        assertThat(new ClaudeMode(null, "max").commands()).containsExactly("/effort max");
        assertThat(new ClaudeMode("sonnet", null).commands()).containsExactly("/model sonnet");
    }

    /// A blank pick is "as is" too — the combos never yield one, but a blank
    /// must not become a bare `/model ` that opens Claude's picker.
    @Test
    void blankCountsAsUnset() {
        assertThat(new ClaudeMode("  ", "").commands()).isEmpty();
    }
}
