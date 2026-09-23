package com.contextswitcher.ui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~whats-new-upstream~7]
// [utest->dsn~restart-to-update~11]
class NewsTitleTest {

    private static final WhatsNew.Item ITEM = new WhatsNew.Item("me", "2026-09-13", "Added", List.of("**x**"));

    /// The count is measured against the announced copy, so the heading names
    /// that basis and the upstream commit only as where a restart leads.
    @Test
    void theCountNamesItsOwnBasis() {
        assertThat(UpdateNews.newsTitle(List.of(ITEM), "abc1234 (2026-09-13 10:00)"))
                .isEqualTo("What's new — 1 pending change since you last looked — now at abc1234 (2026-09-13 10:00)");
    }

    @Test
    void noUpstreamLeavesTheCommitOff() {
        assertThat(UpdateNews.newsTitle(List.of(ITEM, ITEM), null))
                .isEqualTo("What's new — 2 pending changes since you last looked");
        assertThat(UpdateNews.newsTitle(List.of(), null)).isEqualTo("What's new");
    }

    @Test
    void theBadgeCapsAt99() {
        assertThat(Badge.text(7)).isEqualTo("7");
        assertThat(Badge.text(120)).isEqualTo("99+");
    }
}
