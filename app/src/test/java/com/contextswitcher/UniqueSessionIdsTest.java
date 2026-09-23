package com.contextswitcher;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Field report 2026-09-12: `@687` and `@693` both published
/// `f2d88857-…` — a Claude session whose `$TMUX_PANE` was empty had stamped
/// the option onto the session's *current* window as well as its own. The
/// mirror then refused `@693` ("now hosts another Claude session") for the
/// task whose window it was, and the reconcile suspended that task.
// [utest->dsn~tmux-window-ownership~4]
class UniqueSessionIdsTest {

    @Test
    void anIdTwoWindowsClaimIsNoEvidenceOfOwnership() {
        Map<String, String> published = new LinkedHashMap<>();
        published.put("host @687", "f2d88857");
        published.put("host @693", "f2d88857");
        published.put("host @700", "fdd1e565");

        assertThat(Main.uniqueSessionIds(published))
                .containsExactly(Map.entry("host @700", "fdd1e565"));
    }

    /// Nothing to drop: the map is handed back as it is.
    @Test
    void uniqueIdsAreKept() {
        Map<String, String> published = Map.of("host @1", "a", "host @2", "b");

        assertThat(Main.uniqueSessionIds(published)).isEqualTo(published);
    }

    @Test
    void anEmptyMapIsFine() {
        assertThat(Main.uniqueSessionIds(Map.of())).isEmpty();
    }
}
