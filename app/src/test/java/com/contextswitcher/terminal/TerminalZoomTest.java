package com.contextswitcher.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-zoom~4]
class TerminalZoomTest {

    @Test
    void aStepIsOnePointPerNotch() {
        assertThat(TerminalZoom.stepped(14, 1)).isEqualTo(15);
        assertThat(TerminalZoom.stepped(14, -1)).isEqualTo(13);
        assertThat(TerminalZoom.stepped(14, 3)).isEqualTo(17);
    }

    @Test
    void theSizeStaysReadableAndUsable() {
        assertThat(TerminalZoom.stepped(7, -5)).isEqualTo(6);
        assertThat(TerminalZoom.stepped(39, 5)).isEqualTo(40);
    }
}
