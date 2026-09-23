package com.contextswitcher.tasks;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~tmux-sync~7]
class TaskDisposableShellTest {

    private static final Task.TmuxConfig TMUX = new Task.TmuxConfig("0", "@1");

    private static Task shell(String notes) {
        return new Task("t", "bash", TaskStatus.ACTIVE, "h", TMUX,
                null, null, null, null, null, List.of(), List.of(), notes);
    }

    @Test
    void bareImportedShellIsDisposable() {
        assertThat(shell("").isDisposableShell()).isTrue();
        assertThat(shell("\n# Notes\n\n").isDisposableShell()).isTrue();
        assertThat(shell("# Notes").isDisposableShell()).isTrue();
    }

    @Test
    void notesBodyMakesItWorthKeeping() {
        assertThat(shell("# Notes\n\nremember this").isDisposableShell()).isFalse();
    }

    @Test
    void anyRecordedContextMakesItWorthKeeping() {
        Task withClaude = new Task("t", "T", TaskStatus.ACTIVE, "h", TMUX,
                null, null, null, new Task.ClaudeConfig("/w", "sid", null), null, List.of(), List.of(), "");
        Task withNote = new Task("t", "T", TaskStatus.ACTIVE, "h", TMUX,
                null, null, null, null, "onenote:x", List.of(), List.of(), "");
        Task withBrowser = new Task("t", "T", TaskStatus.ACTIVE, "h", TMUX,
                null, null, Task.BrowserConfig.ofUrls("https://example.org"), null, null, List.of(), List.of(), "");
        Task withTag = new Task("t", "T", TaskStatus.ACTIVE, "h", TMUX,
                null, null, null, null, null, List.of(), List.of("keep"), "");

        assertThat(withClaude.isDisposableShell()).isFalse();
        assertThat(withNote.isDisposableShell()).isFalse();
        assertThat(withBrowser.isDisposableShell()).isFalse();
        assertThat(withTag.isDisposableShell()).isFalse();
    }
}
