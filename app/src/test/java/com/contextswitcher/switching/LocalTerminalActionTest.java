package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.tasks.TaskFileParser;
import com.contextswitcher.terminal.WindowsTerminalFocus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A gone tab is reopened in `terminal.folder`, else in the first `folders`
/// entry.
// [utest->dsn~local-terminal-focus~7]
class LocalTerminalActionTest {

    private final List<List<String>> ran = new ArrayList<>();

    /// Focus exits 2 (no such tab), so the action reopens it with `wt`.
    private final LocalCommandRunner runner = new LocalCommandRunner() {
        @Override
        public LocalResult run(List<String> command) {
            ran.add(command);
            return command.getFirst().equals("wt") ? new LocalResult(0, "", "") : new LocalResult(2, "", "");
        }
    };

    private String reopenedIn(String frontmatter) throws Exception {
        var task = new TaskFileParser().parse("t", "---\n" + frontmatter + "---\n");
        new LocalTerminalAction(new WindowsTerminalFocus(runner)).run(task);
        return ran.getLast().getLast();
    }

    @Test
    void terminalFolderWinsOverFolders() throws Exception {
        assertThat(reopenedIn("terminal:\n  tabTitle: t\n  folder: C:\\tab\nfolders:\n  - C:\\explorer\n"))
                .isEqualTo("C:\\tab");
    }

    @Test
    void withoutTerminalFolderTheFirstFolderIsUsed() throws Exception {
        assertThat(reopenedIn("terminal:\n  tabTitle: t\nfolders:\n  - C:\\explorer\n  - C:\\other\n"))
                .isEqualTo("C:\\explorer");
    }
}
