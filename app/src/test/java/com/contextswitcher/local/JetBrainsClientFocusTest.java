package com.contextswitcher.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~gateway-url-action~12]
class JetBrainsClientFocusTest {

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            /data/koppor/jabref-workspaces/2026-07-13-pr16190-hayagriva, 2026-07-13-pr16190-hayagriva
            /home/o/repo/,                                             repo
            hayagriva,                                                 hayagriva
            """)
    void projectNameIsTheLastPathSegment(String projectPath, String expected) {
        assertThat(JetBrainsClientFocus.projectName(projectPath)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            hayagriva,      hayagriva
            "don't-crash",  "don''t-crash"
            """)
    void escapeDoublesSingleQuotes(String value, String expected) {
        assertThat(JetBrainsClientFocus.escape(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            hayagriva
            """)
    void commandIsEncodedPowershellWithoutRawScript(String project) {
        assertThat(JetBrainsClientFocus.command(project))
                .startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand")
                .hasSize(5);
    }

    /// A client on another desktop that cannot be moved here is left alone
    /// (exit 3) but still counts as found: relaunching the Gateway URL would
    /// only prompt or open a second client.
    @Test
    void aClientLeftOnAnotherDesktopIsNotRelaunched() {
        assertThat(JetBrainsClientFocus.script("hayagriva"))
                .contains("$outcome = [CsMv]::Focus($p.MainWindowHandle)")
                .contains("if ($outcome -like 'elsewhere*') { exit 3 }")
                .doesNotContain("SwitchToThisWindow");
        assertThat(new JetBrainsClientFocus(exiting(3)).focus("/w/hayagriva")).isTrue();
        assertThat(new JetBrainsClientFocus(exiting(0)).focus("/w/hayagriva")).isTrue();
        assertThat(new JetBrainsClientFocus(exiting(2)).focus("/w/hayagriva")).isFalse();
    }

    private static LocalCommandRunner exiting(int code) {
        return new LocalCommandRunner() {
            @Override
            public LocalResult run(java.util.List<String> command) {
                return new LocalResult(code, "elsewhere: failed: x: hayagriva\n", "");
            }
        };
    }
}
