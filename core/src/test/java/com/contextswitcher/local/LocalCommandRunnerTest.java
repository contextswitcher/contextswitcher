package com.contextswitcher.local;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~local-terminal-focus~5]
class LocalCommandRunnerTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void doesNotAttemptAWindowsOnlyProgramElsewhere() {
        LocalCommandRunner.LocalResult result =
                new LocalCommandRunner().run(List.of("powershell", "-NoProfile", "-Command", "exit 0"));
        assertThat(result.ok()).isFalse();
        assertThat(result.stderr()).isEqualTo("powershell is Windows-only");
    }

    @Test
    void stillRunsOrdinaryPrograms() {
        assertThat(new LocalCommandRunner().run(List.of("git", "--version")).stdout())
                .startsWith("git version");
    }
}
