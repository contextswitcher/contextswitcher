package com.contextswitcher.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~claude-pr-refresh~5]
class WorkspacePrLookupTest {

    @Test
    void prUrlIsReadFromGhJson() {
        assertThat(WorkspacePrLookup.parsePrUrl("{\"url\":\"https://github.com/JabRef/jabref/pull/16631\"}"))
                .isEqualTo("https://github.com/JabRef/jabref/pull/16631");
    }

    /// `gh` prints its "no pull requests found" on stderr and exits non-zero;
    /// an empty or unrelated stdout must not yield a URL either.
    @Test
    void noPrYieldsNull() {
        assertThat(WorkspacePrLookup.parsePrUrl("")).isNull();
        assertThat(WorkspacePrLookup.parsePrUrl("{\"url\":\"https://github.com/o/r/issues/9\"}")).isNull();
    }

    @Test
    void commandCdsIntoTheQuotedWorkspace() {
        assertThat(WorkspacePrLookup.remoteCommand("/data/koppor/my work"))
                .containsExactly("cd", "'/data/koppor/my work'", "&&", "gh", "pr", "view", "--json", "url");
    }

    /// Each workspace in its own subshell, `;`-joined, so a missing directory
    /// or a branch without a PR (`gh` exits 1) neither leaks a `cd` into the
    /// next one nor ends the batch.
    @Test
    void batchRunsEveryWorkspaceInASubshellSeparatedByMarkers() {
        assertThat(String.join(" ", WorkspacePrLookup.batchCommand(List.of("/a", "/b c"))))
                .isEqualTo("echo '===contextswitcher-workspace /a===' ; "
                        + "( cd '/a' && gh pr view --json url ) ; "
                        + "echo '===contextswitcher-workspace /b c===' ; "
                        + "( cd '/b c' && gh pr view --json url )");
        assertThat(WorkspacePrLookup.batchCommand(List.of("/a", "/b c")))
                .noneMatch(part -> part.contains("\""));
    }

    @Test
    void batchOutputIsSplitPerWorkspace() {
        String stdout = """
                ===contextswitcher-workspace /a===
                {"url":"https://github.com/JabRef/jabref/pull/16631"}
                ===contextswitcher-workspace /no-pr===
                ===contextswitcher-workspace /b c===
                {"url":"https://github.com/o/r/pull/2"}
                """;

        assertThat(WorkspacePrLookup.parseBatch(stdout)).containsExactly(
                Map.entry("/a", "https://github.com/JabRef/jabref/pull/16631"),
                Map.entry("/b c", "https://github.com/o/r/pull/2"));
    }

    @Test
    void localCommandHasNoCd() {
        assertThat(WorkspacePrLookup.localCommand())
                .containsExactly("gh", "pr", "view", "--json", "url");
    }

    /// A workspace that exists here is run locally; a remote-only path (and a
    /// file, and nonsense) falls through to ssh.
    @Test
    void onlyAnExistingDirectoryIsLocal(@TempDir Path temp) {
        assertThat(WorkspacePrLookup.localWorkspace(temp.toString())).isEqualTo(temp);
        assertThat(WorkspacePrLookup.localWorkspace(
                temp.resolve("no-such-worktree").toString())).isNull();
    }
}
