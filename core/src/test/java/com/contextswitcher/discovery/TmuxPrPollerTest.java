package com.contextswitcher.discovery;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~claude-pr-refresh~5]
class TmuxPrPollerTest {

    @Test
    void commandReadsWindowIdAndCsPrWithoutDoubleQuotes() {
        assertThat(TmuxPrPoller.prCommand())
                .containsExactly("tmux", "list-windows", "-a", "-F", "'#{window_id}|#{@cs_pr}'")
                .noneMatch(part -> part.contains("\""));
    }

    @Test
    void parsesOnlyWindowsWithANonEmptyPrOption() {
        Map<String, String> out = new HashMap<>();
        String stdout = """
                @17|https://github.com/o/r/pull/7
                @18|
                @19|https://github.com/o/r/pull/9
                """;

        TmuxPrPoller.parseInto("koppor@devbox", stdout, out);

        assertThat(out).containsOnly(
                Map.entry("koppor@devbox @17", "https://github.com/o/r/pull/7"),
                Map.entry("koppor@devbox @19", "https://github.com/o/r/pull/9"));
    }
}
