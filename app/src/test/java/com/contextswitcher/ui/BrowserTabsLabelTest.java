package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~claude-session-kill~6]
class BrowserTabsLabelTest {

    /// The number is what is **open**, not what the task lists, so zero has to
    /// read as "nothing to close here" rather than as a bare offer — the tick
    /// is disabled at that point and the label is the only thing saying why.
    @Test
    void nothingOpenSaysSo() {
        assertThat(MainWindow.browserTabsLabel(0)).isEqualTo("Close browser tabs — none open");
    }

    /// Unasked is not the same as none open: the tabs may well be there, and
    /// offering to close them would promise something that cannot happen.
    @Test
    void anUnaskableBrowserSaysThatInstead() {
        assertThat(MainWindow.browserTabsLabel(-1))
                .isEqualTo("Close browser tabs — no browser extension connected");
    }

    /// Never "Close 1 PR" — that reads as closing the pull request on GitHub.
    @Test
    void countedTabsAreNamedBrowserTabs() {
        assertThat(MainWindow.browserTabsLabel(1)).isEqualTo("Close 1 browser tab");
        assertThat(MainWindow.browserTabsLabel(7)).isEqualTo("Close 7 browser tabs");
    }

    @Test
    void theCheckingLabelNamesBrowserTabsToo() {
        assertThat(MainWindow.BROWSER_TABS_CHECKING).startsWith("Close browser tabs");
    }
}
