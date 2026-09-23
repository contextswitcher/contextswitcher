package com.contextswitcher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-fork~1]
class ForkPromptTest {

    @Test
    void namesTheSourceTaskAndFencesTheInstructionVerbatim() {
        String prompt = Main.forkPrompt("Fix the NPE when saving twice", "also add a test",
                null, "/home/carl/jabref", null, false, "jabref");
        assertThat(prompt)
                .contains("a fork of the conversation of task \"Fix the NPE when saving twice\"")
                .contains("\n-----\nalso add a test\n-----")
                // Explicit pane target, like a live task's title-publishing clause.
                .contains("tmux set -w -t \"$TMUX_PANE\" @cs_title");
    }

    @Test
    void checksTheRecordedWorkspaceOverTheCwd() {
        String prompt = Main.forkPrompt("t", "do x", "/home/carl/jabref/worktrees/t",
                "/home/carl/jabref", null, false, "jabref");
        assertThat(prompt).contains("Then check the workspace /home/carl/jabref/worktrees/t");
    }

    @Test
    void fallsBackToTheCwdWhenNoWorkspaceWasRecorded() {
        String prompt = Main.forkPrompt("t", "do x", null, "/home/carl/jabref", null, false, "jabref");
        assertThat(prompt).contains("Then check the workspace /home/carl/jabref");
    }

    @Test
    void blankWorkspaceAlsoFallsBackToTheCwd() {
        String prompt = Main.forkPrompt("t", "do x", "   ", "/home/carl/jabref", null, false, "jabref");
        assertThat(prompt).contains("Then check the workspace /home/carl/jabref");
    }

    @Test
    void bootstrapPointsAtThePrimaryCloneAndNamesTheRepository() {
        String prompt = Main.forkPrompt("t", "do x", null, "/data/jabref",
                "https://github.com/JabRef/jabref", true, "jabref");
        assertThat(prompt)
                .contains("git worktree for this task from the jabref subdirectory")
                .contains("The GitHub repository is https://github.com/JabRef/jabref.");
    }

    @Test
    void noBootstrapAddsNoWorktreeClause() {
        String prompt = Main.forkPrompt("t", "do x", null, "/data/jabref", null, false, "jabref");
        assertThat(prompt).doesNotContain("worktree");
    }
}
