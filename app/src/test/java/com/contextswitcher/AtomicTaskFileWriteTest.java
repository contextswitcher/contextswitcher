package com.contextswitcher;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-file-atomic-write~1]
class AtomicTaskFileWriteTest {

    @Test
    void overwritesInPlaceAndLeavesNoTempFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("task.md");
        Files.writeString(file, "---\ntitle: old\n---\n");

        Main.writeAtomically(file, "---\ntitle: new\n---\n");

        assertThat(Files.readString(file)).isEqualTo("---\ntitle: new\n---\n");
        assertThat(Files.exists(dir.resolve("task.md.tmp"))).isFalse();
    }

    @Test
    void createsAFileThatDidNotExist(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("fresh.md");

        Main.writeAtomically(file, "hello");

        assertThat(Files.readString(file)).isEqualTo("hello");
    }
}
