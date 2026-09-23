package com.contextswitcher.terminal;

import com.contextswitcher.local.RequiredTools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-local~4]
class LocalClaudeLauncherTest {

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void ownedSessionRunsClaudeWithThePromptAndNoWindowsTerminal() {
        assertThat(LocalClaudeLauncher.ownedCommand("Fix the importer", "opus", null))
                .containsExactly("cmd", "/k", "claude", "--model", "opus", "Fix the importer");
    }

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void ownedSessionResumesATranscriptWithoutModelOrPrompt() {
        assertThat(LocalClaudeLauncher.ownedCommand("Fix the importer", "opus", "a1b2-c3"))
                .containsExactly("cmd", "/k", "claude", "--resume", "a1b2-c3");
    }

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void ownedSessionFoldsNewlinesSoTheCommandLineStaysOneLine() {
        assertThat(LocalClaudeLauncher.ownedCommand("First\nsecond", null, null).getLast())
                .isEqualTo("First second");
    }

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void ownedSessionTurnsDoubleQuotesSoCmdMetacharactersStayQuoted() {
        assertThat(LocalClaudeLauncher.ownedCommand("chevron left \"<\" & more", null, null).getLast())
                .isEqualTo("chevron left '<' & more");
    }

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void omitsAnEmptyPrompt() {
        assertThat(LocalClaudeLauncher.ownedCommand("   ", null, null))
                .containsExactly("cmd", "/k", "claude");
    }

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void ownedPasteBracketsTheMessageAndTypesLineBreaksAsEnter() {
        assertThat(LocalClaudeLauncher.ownedPaste("First\nsecond\r\nthird"))
                .isEqualTo((char) 27 + "[200~First\rsecond\rthird" + (char) 27 + "[201~");
    }

    @Test
    void opensATmuxWindowNamedAfterTheDirectoryElsewhere() {
        assertThat(LocalClaudeLauncher.tmuxCommand("/data/ws/jabref", "Fix the importer", "opus"))
                .containsExactly(RequiredTools.resolve("tmux"), "new-window",
                        "-P", "-F", "#{window_id}", "-t", "0:",
                        "-c", "/data/ws/jabref",
                        "-n", "jabref", "claude", "--model", "opus", "Fix the importer");
    }

    @Test
    void createsTheSessionWhenTmuxHasNone() {
        assertThat(LocalClaudeLauncher.tmuxSessionCommand("/data/ws/jabref", "Fix it", null))
                .containsExactly(RequiredTools.resolve("tmux"), "new-session", "-d",
                        "-P", "-F", "#{window_id}", "-s", "0",
                        "-c", "/data/ws/jabref",
                        "-n", "jabref", "claude", "Fix it");
    }

    @Test
    void keepsTheTmuxPromptVerbatimSinceNoShellReparsesIt() {
        assertThat(LocalClaudeLauncher.tmuxCommand("/data/ws", "First; then\n\"second\"", null)
                .getLast())
                .isEqualTo("First; then\n\"second\"");
    }
}
