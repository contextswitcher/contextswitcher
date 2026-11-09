package com.contextswitcher.ui;

import java.util.List;

import com.contextswitcher.extension.ExtensionProtocol.TabWindow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~browser-url-order~1]
class BrowserTabsTest {

    private static final String A = "https://example.org/a";
    private static final String B = "https://example.org/b";
    private static final String C = "https://example.org/c";

    private static List<TabWindow> tabs(String... urls) {
        return List.of(new TabWindow(1, "", List.of(urls)));
    }

    @Test
    void entriesFollowTheTabsAndKeepTheirTitles() {
        assertThat(BrowserTabs.inTabOrder(List.of(A + " — first", B), tabs(B, A)))
                .containsExactly(B, A + " — first");
    }

    @Test
    void anEntryWithoutATabKeepsItsPlace() {
        assertThat(BrowserTabs.inTabOrder(List.of(A, B, C), tabs(C, A))).containsExactly(C, B, A);
    }

    @Test
    void aTabOnASubPageCountsForItsEntryAcrossWindows() {
        List<TabWindow> windows = List.of(new TabWindow(1, "", List.of(B + "/files")), new TabWindow(2, "", List.of(A)));
        assertThat(BrowserTabs.inTabOrder(List.of(A, B), windows)).containsExactly(B, A);
    }

    @Test
    void titleIsTheExactTabsElseOneUnderIt() {
        List<TabWindow> windows = List.of(new TabWindow(1, "", List.of(A + "/x", A), List.of("Under", "Exact")));
        assertThat(BrowserTabs.titleOf(A, windows)).contains("Exact");
        windows = List.of(new TabWindow(1, "", List.of(A + "/x"), List.of("Under")));
        assertThat(BrowserTabs.titleOf(A, windows)).contains("Under");
        assertThat(BrowserTabs.titleOf(B, windows)).isEmpty();
        assertThat(BrowserTabs.titleOf(A, tabs(A))).isEmpty();
    }
}
