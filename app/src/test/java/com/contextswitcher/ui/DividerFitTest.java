package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// [utest->dsn~divider-fit-content~1]
class DividerFitTest {

    @Test
    void movesTheDividerRightForAPaneLeftOfIt() {
        assertThat(DividerFit.widened(0.25, 1000, 50, true)).isCloseTo(0.30, within(1e-9));
    }

    @Test
    void movesTheDividerLeftForAPaneRightOfIt() {
        assertThat(DividerFit.widened(0.60, 1000, 100, false)).isCloseTo(0.50, within(1e-9));
    }

    @Test
    void staysWithinTheSplit() {
        assertThat(DividerFit.widened(0.9, 1000, 500, true)).isEqualTo(1.0);
        assertThat(DividerFit.widened(0.1, 1000, 500, false)).isEqualTo(0.0);
    }
}
