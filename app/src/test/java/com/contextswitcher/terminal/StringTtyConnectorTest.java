package com.contextswitcher.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-suspend-snapshot~3]
class StringTtyConnectorTest {

    // A bare LF only moves the emulator's cursor down, so a snapshot replayed
    // as captured rendered as a staircase of overlapping lines.
    @Test
    void lineFeedsAreReplayedAsCarriageReturnLineFeeds() {
        assertThat(replay("first\n  second\n")).isEqualTo("first\r\n  second\r\n");
    }

    @Test
    void carriageReturnsAlreadyThereAreNotDoubled() {
        assertThat(replay("first\r\nsecond")).isEqualTo("first\r\nsecond");
    }

    /// Everything the connector hands out, read in small chunks so the
    /// buffering is exercised too.
    private static String replay(String content) {
        StringTtyConnector connector = new StringTtyConnector(content);
        StringBuilder read = new StringBuilder();
        char[] buffer = new char[3];
        int count;
        while ((count = connector.read(buffer, 0, buffer.length)) > 0) {
            read.append(buffer, 0, count);
        }
        assertThat(count).as("the stream ends").isEqualTo(-1);
        return read.toString();
    }
}
