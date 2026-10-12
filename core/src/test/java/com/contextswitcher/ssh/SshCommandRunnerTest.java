package com.contextswitcher.ssh;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~ssh-command-runner~8]
class SshCommandRunnerTest {

    /// A leading tilde stays outside the quotes, so the remote shell expands
    /// it; everything else is quoted as one word.
    /// Neither a quote nor a backslash stays inside the quotes, where fish
    /// reads `\'` and `\\` as escapes and sh does not: the twice-quoted
    /// "can't" a fish login shell rejected.
    @Test
    void quoteKeepsBackslashesAndQuotesOutsideTheQuotes() {
        assertThat(SshCommandRunner.quote("can't")).isEqualTo("'can'\\''t'");
        assertThat(SshCommandRunner.quote("a\\;b")).isEqualTo("'a'\\\\';b'");
        assertThat(SshCommandRunner.quote(SshCommandRunner.quote("can't")))
                .isEqualTo("''\\''can'\\'''\\\\''\\'''\\''t'\\'''");
    }

    @Test
    void quotePathLeavesTheTildeExpandable() {
        assertThat(SshCommandRunner.quotePath("~/workspaces/a b")).isEqualTo("~/'workspaces/a b'");
        assertThat(SshCommandRunner.quotePath("~")).isEqualTo("~");
        assertThat(SshCommandRunner.quotePath("/home/me/it's")).isEqualTo("'/home/me/it'\\''s'");
        assertThat(SshCommandRunner.quotePath("~other/x")).isEqualTo("'~other/x'");
    }
}
