package com.contextswitcher.discovery;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~claude-title-sync~3]
class TmuxTitlePollerTest {

    @Test
    void parseCollectsOnlyWindowsWithATitleAndKeepsPipesInTheTitle() {
        Map<String, String> out = new HashMap<>();

        TmuxTitlePoller.parseInto("h", """
                @1|Fix NPE on double save
                @2|
                @3|a|b
                """, out);

        assertThat(out).containsOnly(
                Map.entry("h @1", "Fix NPE on double save"),
                Map.entry("h @3", "a|b"));
    }

    @Test
    void commandsAreFreeOfDoubleQuotesAndTargetTheWindow() {
        assertThat(TmuxTitlePoller.titleCommand())
                .containsExactly("tmux", "list-windows", "-a", "-F", "'#{window_id}|#{@cs_title}'");
        assertThat(TmuxTitlePoller.unsetCommand("@7"))
                .containsExactly("tmux", "set", "-w", "-u", "-t", "'@7'", "@cs_title")
                .noneMatch(part -> part.contains("\""));
    }
}
