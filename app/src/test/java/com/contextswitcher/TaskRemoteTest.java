package com.contextswitcher;

import java.util.Map;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import com.contextswitcher.ui.MainWindow;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Field report 2026-09-12: "claude button should automatically add the
/// default remote so that this is easy … meaning: claude means: tmux +
/// claude". A task written by hand carries no `remote:`, and the terminal
/// placeholder's window buttons were gated on one — a dead end that asked for
/// a frontmatter line first.
// [utest->dsn~remote-window-choice~6]
class TaskRemoteTest {

    private static final String GROUP_CONFIG = """
            ---
            remote: koppor@devbox
            workspacesRoot: /data/koppor/ws
            ---
            notes
            """;

    @Test
    void aTaskWithoutAHostBorrowsItsCategorys() {
        assertThat(Main.taskRemote(files(Map.of("jabref/CONTEXTSWITCHER.md", GROUP_CONFIG)),
                task("jabref/fix", null)))
                .isEqualTo("koppor@devbox");
    }

    /// A task deliberately moved to another host is never pulled back to the
    /// category's.
    @Test
    void theTasksOwnHostWins() {
        assertThat(Main.taskRemote(files(Map.of("jabref/CONTEXTSWITCHER.md", GROUP_CONFIG)),
                task("jabref/fix", "me@elsewhere")))
                .isEqualTo("me@elsewhere");
    }

    @Test
    void neitherHostMeansNone() {
        assertThat(Main.taskRemote(files(Map.of()), task("jabref/fix", null))).isNull();
        assertThat(Main.taskRemote(files(Map.of("CONTEXTSWITCHER.md", GROUP_CONFIG)),
                task("root-level", null)))
                .as("a task outside any category has no category to borrow from")
                .isNull();
    }

    private static Task task(String id, @Nullable String remote) {
        return new Task(id, "t", TaskStatus.ACTIVE, remote, null, null, null, null, null, null, "");
    }

    /// The few `TaskFileAccess` methods the lookup touches; everything else
    /// would be a lie to implement.
    private static MainWindow.TaskFileAccess files(Map<String, String> byName) {
        return new MainWindow.TaskFileAccess() {
            @Override
            public String load(String fileName) {
                return byName.getOrDefault(fileName, "");
            }

            @Override
            public @Nullable String read(String fileName) {
                return byName.get(fileName);
            }

            @Override
            public boolean exists(String fileName) {
                return byName.containsKey(fileName);
            }

            @Override
            public @Nullable String save(String fileName, String content) {
                throw new UnsupportedOperationException();
            }

            @Override
            public @Nullable String delete(String fileName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public @Nullable String move(String fromFileName, String toFileName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public @Nullable String createFolder(String folder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public @Nullable String renameFolder(String folder, String newFolder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public @Nullable String deleteFolder(String folder) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
