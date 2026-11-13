package com.contextswitcher.ui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~owned-session-close-confirm~1]
class CloseConfirmationTest {

    @Test
    void groupsByCategoryAlphabeticallyWithTheRootLastAndTasksByTitle() {
        var grouped = CloseConfirmation.byCategory(List.of(
                new CloseConfirmation.Row("2026-sofen/b", "Tomorrow's plate"),
                new CloseConfirmation.Row("loose", "A root task"),
                new CloseConfirmation.Row("2026-prog1/x", "WIF Karel"),
                new CloseConfirmation.Row("2026-sofen/a", "SWEN slides"),
                new CloseConfirmation.Row("2026-prog1/y", "fable investigation")));

        assertThat(grouped.keySet()).containsExactly("2026-prog1", "2026-sofen", "");
        assertThat(grouped.get("2026-prog1")).extracting(CloseConfirmation.Row::title)
                .containsExactly("fable investigation", "WIF Karel");
        assertThat(grouped.get("2026-sofen")).extracting(CloseConfirmation.Row::taskId)
                .containsExactly("2026-sofen/a", "2026-sofen/b");
    }

    @Test
    void nothingRunningMeansNoGroups() {
        assertThat(CloseConfirmation.byCategory(List.of())).isEmpty();
    }
}
