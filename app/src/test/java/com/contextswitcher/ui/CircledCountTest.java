package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~message-queue-count-badge~1]
class CircledCountTest {

    @Test
    void nonPositiveCountsHaveNoBadge() {
        assertThat(CircledCount.glyph(0)).isEmpty();
        assertThat(CircledCount.glyph(-3)).isEmpty();
    }

    @Test
    void mapsOneThroughTwentyToTheNegativeCircledGlyphs() {
        assertThat(CircledCount.glyph(1)).isEqualTo("➊");   // ➊
        assertThat(CircledCount.glyph(10)).isEqualTo("➓");  // ➓
        assertThat(CircledCount.glyph(11)).isEqualTo("⓫");  // ⓫
        assertThat(CircledCount.glyph(20)).isEqualTo("⓴");  // ⓴
    }

    @Test
    void capsAtTwenty() {
        assertThat(CircledCount.glyph(21)).isEqualTo("⓴");
        assertThat(CircledCount.glyph(999)).isEqualTo("⓴");
    }
}
