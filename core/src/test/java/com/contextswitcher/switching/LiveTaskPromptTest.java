package com.contextswitcher.switching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-live~8]
class LiveTaskPromptTest {

    @Test
    void promptCarriesTheDescriptionBetweenFenceLinesAndAsksForATitle() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("Fix the NPE when saving twice", null, false, "jabref", null);
        assertThat(prompt)
                // Explicit pane target, else a bare `set -w` lands the option
                // on whichever window is focused, not the task's.
                .contains("tmux set -w -t \"$TMUX_PANE\" @cs_title")
                .contains("\n-----\nFix the NPE when saving twice\n-----")
                .doesNotContain("worktree");
    }

    @Test
    void bootstrapPointsAtThePrimaryCloneAndNamesTheRepository() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("fix npe", "https://github.com/JabRef/jabref", true, "jabref", null);
        assertThat(prompt)
                .contains("git worktree for this task from the jabref subdirectory")
                .contains("The GitHub repository is https://github.com/JabRef/jabref.")
                .doesNotContain("Ask me for");
    }

    @Test
    void bootstrapWithoutRepoAsksNothing() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("fix npe", null, true, "jabref", null);
        assertThat(prompt)
                .contains("git worktree for this task from the jabref subdirectory")
                .doesNotContain("Ask me")
                .doesNotContain("repository is");
    }

    /// Reference material a caller appends — the workspace-root `CLAUDE.md`
    /// template of a category created from a URL — follows the closing fence
    /// and never touches the description.
    // [utest->dsn~claude-md-templates~1]
    @Test
    void appendixFollowsTheClosingFence() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("set up sailor", null, false, "sailor",
                "# <project>-workspaces layout and workflow\n");
        assertThat(prompt)
                .contains("\n-----\nset up sailor\n-----\n\n"
                        + "# <project>-workspaces layout and workflow")
                .endsWith("layout and workflow");
    }

    @Test
    void blankAppendixAddsNothing() {
        assertThat(ClaudeWindowLauncher.liveTaskPrompt("set up sailor", null, false, "sailor", "  \n"))
                .endsWith("\n-----\nset up sailor\n-----");
    }

    // Delivery is stdin into a tmux buffer (QueueSendCommands), so the
    // description keeps its quotes verbatim — no stripping.
    @Test
    void descriptionQuotesSurvive() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("rename \"foo\" to 'bar'", null, false, "g", null);
        assertThat(prompt).contains("rename \"foo\" to 'bar'");
    }
}
