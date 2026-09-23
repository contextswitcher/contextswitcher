package com.contextswitcher.switching;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.LocalFolderFocus;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~explorer-folder-focus~3]
class ExplorerFolderActionTest {

    /// A focus that returns a canned local result, so no PowerShell runs —
    /// pinned to the Windows path, which is the one driving a runner.
    private static LocalFolderFocus focusReturning(int exitCode, String stdout) {
        return LocalFolderFocus.forWindows(new LocalCommandRunner() {
            @Override
            public LocalResult run(java.util.List<String> command) {
                return new LocalResult(exitCode, stdout, "");
            }
        });
    }

    /// A focus failing exactly for the named folders (exit 2), succeeding for
    /// the rest — the script embeds the folder, so the command reveals which.
    private static LocalFolderFocus focusFailingFor(String... missing) {
        return LocalFolderFocus.forWindows(new LocalCommandRunner() {
            @Override
            public LocalResult run(java.util.List<String> command) {
                String script = new String(java.util.Base64.getDecoder()
                        .decode(command.getLast()), java.nio.charset.StandardCharsets.UTF_16LE);
                boolean fails = java.util.Arrays.stream(missing).anyMatch(script::contains);
                return new LocalResult(fails ? 2 : 0, fails ? "missing" : "focused", "");
            }
        });
    }

    private static Task task(@org.jspecify.annotations.Nullable String folder) {
        return new Task("t", "T", TaskStatus.ACTIVE, null, null, null, null, null, null, null, folder, "");
    }

    private static Task tasks(java.util.List<String> folders) {
        return new Task("t", "T", TaskStatus.ACTIVE, null, null, null, null, null, null, null,
                folders, java.util.List.of(), "");
    }

    @Test
    void notConfiguredWithoutAFolder() {
        assertThat(new ExplorerFolderAction(focusReturning(0, "")).isConfigured(task(null))).isFalse();
        assertThat(new ExplorerFolderAction(focusReturning(0, "")).isConfigured(task("C:\\p"))).isTrue();
    }

    @Test
    void succeedsWhenExplorerFocusesOrOpens() {
        ActionResult result = new ExplorerFolderAction(focusReturning(0, "focused: C:\\p"))
                .run(task("C:\\p"));
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("focused: C:\\p");
    }

    @Test
    void failsWhenTheFolderIsMissing() {
        ActionResult result = new ExplorerFolderAction(focusReturning(2, "missing: C:\\gone"))
                .run(task("C:\\gone"));
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("folder not found");
    }

    @Test
    void everyConfiguredFolderIsOpened() {
        ActionResult result = new ExplorerFolderAction(focusFailingFor())
                .run(tasks(java.util.List.of("C:\\one", "C:\\two", "C:\\three")));
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("3 folders: C:\\one, C:\\two, C:\\three");
    }

    /// A missing folder does not stop the others, and the detail names it.
    @Test
    void oneMissingFolderFailsTheChipButTheOthersStillOpen() {
        ActionResult result = new ExplorerFolderAction(focusFailingFor("C:\\gone"))
                .run(tasks(java.util.List.of("C:\\one", "C:\\gone", "C:\\three")));
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo("1 of 3 failed: C:\\gone");
    }
}
