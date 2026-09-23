package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The caret arithmetic behind Ctrl+A/Ctrl+E/Ctrl+U/Ctrl+W — plain string
/// logic, so it is pinned here rather than through a robot (the chord wiring
/// itself is [ReadlineKeysUiTest]).
// [utest->dsn~readline-keys~1]
class ReadlineKeysTest {

    private static final String TEXT = "one two\nthree four\n";

    @Test
    void lineStartIsTheTextStartOnTheFirstLine() {
        assertThat(ReadlineKeys.lineStart(TEXT, 0)).isZero();
        assertThat(ReadlineKeys.lineStart(TEXT, 5)).isZero();
        assertThat(ReadlineKeys.lineStart("no breaks", 4)).isZero();
    }

    @Test
    void lineStartIsJustAfterThePrecedingBreak() {
        assertThat(ReadlineKeys.lineStart(TEXT, 8)).isEqualTo(8);
        assertThat(ReadlineKeys.lineStart(TEXT, 14)).isEqualTo(8);
        // The caret sitting on the empty last line after the trailing break.
        assertThat(ReadlineKeys.lineStart(TEXT, TEXT.length())).isEqualTo(19);
    }

    @Test
    void lineEndIsTheNextBreakOrTheTextEnd() {
        assertThat(ReadlineKeys.lineEnd(TEXT, 0)).isEqualTo(7);
        assertThat(ReadlineKeys.lineEnd(TEXT, 7)).isEqualTo(7);
        assertThat(ReadlineKeys.lineEnd(TEXT, 8)).isEqualTo(18);
        assertThat(ReadlineKeys.lineEnd(TEXT, TEXT.length())).isEqualTo(TEXT.length());
        assertThat(ReadlineKeys.lineEnd("no breaks", 3)).isEqualTo(9);
    }

    @Test
    void wordStartTakesAWholeWhitespaceDelimitedWord() {
        // bash's unix-word-rubout is whitespace-delimited: the dotted path goes
        // in one Ctrl+W, unlike the punctuation-aware Ctrl+Backspace.
        assertThat(ReadlineKeys.wordStart("cd /data/koppor.dev", 17)).isEqualTo(3);
        assertThat(ReadlineKeys.wordStart("one two", 7)).isEqualTo(4);
    }

    @Test
    void wordStartSkipsTheWhitespaceUnderTheCaretFirst() {
        assertThat(ReadlineKeys.wordStart("one two   ", 10)).isEqualTo(4);
        assertThat(ReadlineKeys.wordStart("   ", 3)).isZero();
        assertThat(ReadlineKeys.wordStart("", 0)).isZero();
    }
}
