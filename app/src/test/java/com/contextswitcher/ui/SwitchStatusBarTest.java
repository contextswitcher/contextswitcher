package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The status bar is one line high, whatever it is handed.
// [utest->dsn~status-bar-one-line~2]
class SwitchStatusBarTest {

    @Test
    void foldsAMultiLineTextOntoOneLine() {
        assertThat(SwitchStatusBar.oneLine("Cannot start Claude:\nssh: connect failed\n\n  retry\n"))
                .isEqualTo("Cannot start Claude: ssh: connect failed retry");
    }

    @Test
    void leavesASingleLineAlone() {
        assertThat(SwitchStatusBar.oneLine("Opening https://example.org/pull/1 …"))
                .isEqualTo("Opening https://example.org/pull/1 …");
    }
}
