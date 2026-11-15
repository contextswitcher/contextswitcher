package com.contextswitcher.local;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~local-terminal-focus~7]
class LocalCommandRunnerTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void doesNotAttemptAWindowsOnlyProgramElsewhere() {
        LocalCommandRunner.LocalResult result =
                new LocalCommandRunner().run(List.of("powershell", "-NoProfile", "-Command", "exit 0"));
        assertThat(result.ok()).isFalse();
        assertThat(result.stderr()).isEqualTo("powershell is Windows-only");
    }

    /// What the `uiTest` task sets: no Explorer, no `powershell` desktop
    /// focus, no terminal tabs — but ordinary tools still run.
    @Test
    void desktopProgramsAreNotStartedWhileSideEffectsAreOff() {
        String before = System.getProperty(LocalCommandRunner.NO_DESKTOP_SIDE_EFFECTS);
        System.setProperty(LocalCommandRunner.NO_DESKTOP_SIDE_EFFECTS, "true");
        try {
            for (String program : List.of("powershell", "explorer.exe", "wt", "code")) {
                LocalCommandRunner.LocalResult result = new LocalCommandRunner().run(List.of(program, "x"));
                assertThat(result.ok()).isFalse();
                assertThat(result.stderr()).endsWith("desktop side effects are off");
            }
            assertThat(new LocalCommandRunner().run(List.of("git", "--version")).stdout())
                    .startsWith("git version");
        } finally {
            if (before == null) {
                System.clearProperty(LocalCommandRunner.NO_DESKTOP_SIDE_EFFECTS);
            } else {
                System.setProperty(LocalCommandRunner.NO_DESKTOP_SIDE_EFFECTS, before);
            }
        }
    }

    @Test
    void stillRunsOrdinaryPrograms() {
        assertThat(new LocalCommandRunner().run(List.of("git", "--version")).stdout())
                .startsWith("git version");
    }
}
