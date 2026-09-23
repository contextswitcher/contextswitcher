package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~window-title-category~3]
class WindowTitleTest {

    @Test
    void rootLevelSelectionIsJustTheAppName() {
        assertThat(MainWindow.windowTitle("", 0, 0)).isEqualTo("ContextSwitcher");
    }

    @Test
    void categoryComesFirst() {
        assertThat(MainWindow.windowTitle("JabRef", 0, 0)).isEqualTo("JabRef | ContextSwitcher");
    }

    @Test
    void zeroCountsAreOmitted() {
        assertThat(MainWindow.windowTitle("JabRef", 1, 0))
                .isEqualTo("JabRef | 1 waiting | ContextSwitcher");
        assertThat(MainWindow.windowTitle("JabRef", 0, 2))
                .isEqualTo("JabRef | 2 working | ContextSwitcher");
    }

    @Test
    void waitingBeforeWorking() {
        assertThat(MainWindow.windowTitle("JabRef", 1, 2))
                .isEqualTo("JabRef | 1 waiting | 2 working | ContextSwitcher");
    }
}
