package com.contextswitcher.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-pane~14]
class LeakingCsiFilterTest {

    private static final String ESC = "";

    private final LeakingCsiFilter filter = new LeakingCsiFilter();

    /// Feeds the chunks the way `PtyTtyConnector.read` does — each one behind
    /// the chars the filter holds — and joins what comes out.
    private String feed(String... chunks) {
        StringBuilder result = new StringBuilder();
        for (String chunk : chunks) {
            char[] buf = new char[64];
            int held = filter.held();
            chunk.getChars(0, chunk.length(), buf, 3 + held);
            int length = filter.filter(buf, 3, chunk.length());
            result.append(buf, 3, length);
        }
        return result.toString();
    }

    @Test
    void dropsClaudesKittyKeyboardPopThatJediTermFxPrintsAsALessThan() {
        assertThat(feed("a" + ESC + "[<ub")).isEqualTo("ab");
    }

    @Test
    void dropsAKittyKeyboardSetWithParameters() {
        assertThat(feed("a" + ESC + "[=5;1ub")).isEqualTo("ab");
    }

    @Test
    void keepsEverySequenceJediTermFxParsesProperly() {
        String text = ESC + "[>5u" + ESC + "[?2026h" + ESC + "[1mX" + ESC + "[0m" + ESC + "7";
        assertThat(feed(text)).isEqualTo(text);
    }

    @Test
    void dropsASequenceSplitAcrossReads() {
        assertThat(feed("a" + ESC, "[", "<", "1", "u", "b")).isEqualTo("ab");
    }

    @Test
    void releasesHeldCharsWhenTheSequenceTurnsOutHarmless() {
        assertThat(feed("x" + ESC, "[31my")).isEqualTo("x" + ESC + "[31my");
        assertThat(feed(ESC + "[", "Hz")).isEqualTo(ESC + "[Hz");
    }

    @Test
    void anEscapeRightAfterAnEscapeStartsTheNextSequence() {
        assertThat(feed(ESC + ESC + "[<u")).isEqualTo(ESC);
    }

    @Test
    void aControlCharAbortsTheDroppedSequenceAndIsKept() {
        assertThat(feed(ESC + "[<1\ry")).isEqualTo("\ry");
    }
}
