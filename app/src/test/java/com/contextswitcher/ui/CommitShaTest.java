package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~running-commit~3]
class CommitShaTest {

    /// The status bar line carries the date for the reader; a bug report wants
    /// the sha alone, so the copy stops at the first space.
    @Test
    void theDateAroundTheShaIsNotCopied() {
        assertThat(MainWindow.shaOf("d8ea5fdc7 (2026-09-12 13:28)")).isEqualTo("d8ea5fdc7");
    }

    /// A line git shortened to nothing but the sha copies whole — no
    /// off-by-one on the missing space.
    @Test
    void aBareShaCopiesWhole() {
        assertThat(MainWindow.shaOf("d8ea5fdc7")).isEqualTo("d8ea5fdc7");
    }
}
