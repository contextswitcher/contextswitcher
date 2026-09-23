package com.contextswitcher.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~local-terminal-focus~5]
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
}
