package com.contextswitcher.switching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-live~8]
class LiveTaskPromptTest {

    @Test
    void promptCarriesTheDescriptionBetweenFenceLinesAndAsksForATitle() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("Fix the NPE when saving twice", null, false, "jabref", null, null);
        assertThat(prompt)
                // Explicit pane target, else a bare `set -w` lands the option
                // on whichever window is focused, not the task's.
                .contains("tmux set -w -t \"$TMUX_PANE\" @cs_title")
                .contains("\n-----\nFix the NPE when saving twice\n-----")
                .doesNotContain("worktree");
    }

    @Test
    void bootstrapPointsAtThePrimaryCloneAndNamesTheRepository() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("fix npe", "https://github.com/JabRef/jabref", true, "jabref", null, null);
        assertThat(prompt)
                .contains("git worktree for this task from the jabref subdirectory")
                .contains("The GitHub repository is https://github.com/JabRef/jabref.")
                .doesNotContain("Ask me for");
    }

    @Test
    void bootstrapWithoutRepoAsksNothing() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("fix npe", null, true, "jabref", null, null);
        assertThat(prompt)
                .contains("git worktree for this task from the jabref subdirectory")
                .doesNotContain("Ask me")
                .doesNotContain("repository is");
    }

    /// A category renamed out of a name clash (`contextswitcher` →
    /// `contextswitcher-swkgpu1`) keeps its clone under the old name, so the
    /// clause names the clone's path instead of the group-named subdirectory.
    @Test
    void bootstrapNamesTheMainCheckoutOverTheGroupSubdirectory() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("fix npe", "https://github.com/contextswitcher/contextswitcher",
                true, "contextswitcher-swkgpu1", "/data/kopp/contextswitcher-workspaces/contextswitcher", null);
        assertThat(prompt)
                .contains("git worktree for this task from the primary clone at"
                        + " /data/kopp/contextswitcher-workspaces/contextswitcher and work in it.")
                .doesNotContain("contextswitcher-swkgpu1 subdirectory");
    }

    @Test
    void blankMainCheckoutFallsBackToTheGroupSubdirectory() {
        assertThat(ClaudeWindowLauncher.liveTaskPrompt("fix npe", null, true, "jabref", "  ", null))
                .contains("from the jabref subdirectory of the current directory");
    }

    /// Reference material a caller appends — the workspace-root `CLAUDE.md`
    /// template of a category created from a URL — follows the closing fence
    /// and never touches the description.
    // [utest->dsn~claude-md-templates~1]
    @Test
    void appendixFollowsTheClosingFence() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("set up sailor", null, false, "sailor", null,
                "# <project>-workspaces layout and workflow\n");
        assertThat(prompt)
                .contains("\n-----\nset up sailor\n-----\n\n"
                        + "# <project>-workspaces layout and workflow")
                .endsWith("layout and workflow");
    }

    @Test
    void blankAppendixAddsNothing() {
        assertThat(ClaudeWindowLauncher.liveTaskPrompt("set up sailor", null, false, "sailor", null, "  \n"))
                .endsWith("\n-----\nset up sailor\n-----");
    }

    // Delivery is stdin into a tmux buffer (QueueSendCommands), so the
    // description keeps its quotes verbatim — no stripping.
    @Test
    void descriptionQuotesSurvive() {
        String prompt = ClaudeWindowLauncher.liveTaskPrompt("rename \"foo\" to 'bar'", null, false, "g", null, null);
        assertThat(prompt).contains("rename \"foo\" to 'bar'");
    }
}
