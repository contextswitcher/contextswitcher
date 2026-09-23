package com.contextswitcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// [utest->dsn~memory-log~1]
class MemoryLogTest {

    @Test
    void lineCarriesAllFigures() {
        // The JVM running this test has the extended OS bean, so the full form.
        assertThat(MemoryLog.line()).matches(
                "heap \\d+ MB/\\d+ MB, non-heap \\d+ MB,"
                + " process virtual \\d+ MB, free RAM \\d+ MB, \\d+ threads");
    }
}
