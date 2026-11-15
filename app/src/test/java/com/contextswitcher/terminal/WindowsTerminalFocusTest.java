package com.contextswitcher.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~local-terminal-focus~7]
class WindowsTerminalFocusTest {

    @Test
    void escapeDoublesSingleQuotesForPowerShell() {
        assertThat(WindowsTerminalFocus.escape("it's mine")).isEqualTo("it''s mine");
        assertThat(WindowsTerminalFocus.escape("plain")).isEqualTo("plain");
    }

    @Test
    void scriptEmbedsTheEscapedTitleAndMatchesTerminalTabs() {
        String script = WindowsTerminalFocus.script("jabref's tab");

        assertThat(script).contains("$title = 'jabref''s tab'");
        assertThat(script).contains("CASCADIA_HOSTING_WINDOW_CLASS");
        assertThat(script).contains("SelectionItemPattern");
    }

    @Test
    void commandIsAnEncodedPowerShellCommandDecodingToTheScript() {
        List<String> command = WindowsTerminalFocus.command("mytab");

        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(
                Base64.getDecoder().decode(command.getLast()), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(WindowsTerminalFocus.script("mytab"));
        assertThat(decoded).contains("$title = 'mytab'");
    }

    // [utest->dsn~local-terminal-focus~7]
    @Test
    void openCommandPinsTheTitleAndEscapesSemicolons() {
        assertThat(WindowsTerminalFocus.openCommand("a;b", "C:\\p"))
                .containsExactly("wt", "-w", "0", "new-tab", "--title", "a\\;b",
                        "--suppressApplicationTitle", "-d", "C:\\p");
    }

    /// A tab whose window sits on another desktop and cannot be moved here
    /// is left alone (exit 3): no desktop jump, and no second tab opened.
    // [utest->dsn~local-terminal-focus~7]
    @Test
    void aTabLeftOnAnotherDesktopIsReportedNotJumpedToOrReopened() {
        String script = WindowsTerminalFocus.script("t");
        assertThat(script).contains(com.contextswitcher.local.WindowsWindowMove.TYPES.strip())
                .contains("[CsMv]::Focus($hwnd)")
                .contains("$outcome = [CsMv]::Focus($h)")
                .contains("if ($outcome -like 'elsewhere*') { exit 3 }")
                .doesNotContain("SwitchToThisWindow");
        java.util.List<java.util.List<String>> ran = new java.util.ArrayList<>();
        var runner = new com.contextswitcher.local.LocalCommandRunner() {
            @Override
            public LocalResult run(java.util.List<String> command) {
                ran.add(command);
                return new LocalResult(3, "elsewhere: failed: x: t\n", "");
            }
        };
        var result = new WindowsTerminalFocus(runner).focus("t", "C:\\p");
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo("tab \"t\" is on another desktop");
        assertThat(ran).hasSize(1);
    }
}
