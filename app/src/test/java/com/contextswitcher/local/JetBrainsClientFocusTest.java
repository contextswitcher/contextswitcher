package com.contextswitcher.local;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~gateway-url-action~11]
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
}
