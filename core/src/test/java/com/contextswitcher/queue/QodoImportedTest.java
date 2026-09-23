package com.contextswitcher.queue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~qodo-agent-prompt-queue~11]
class QodoImportedTest {

    @Test
    void savesAndLoadsPromptsWithTheirCommentUrls(@TempDir Path dir) throws Exception {
        Map<String, String> stored = new LinkedHashMap<>();
        stored.put("## Issue description\nFix: it\n", "https://github.com/o/r/pull/7#discussion_r1");
        stored.put("Second prompt", "https://github.com/o/r/pull/7#discussion_r2");

        Path file = QodoImported.file(dir, "group/task");
        QodoImported.save(file, stored);

        assertThat(QodoImported.load(file)).containsExactlyEntriesOf(stored);
    }

    @Test
    void loadsASidecarWrittenBeforeUrlsWereRecorded(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("legacy.yaml");
        Files.writeString(file, "- Old prompt\n- Another one\n");

        assertThat(QodoImported.load(file))
                .containsExactly(Map.entry("Old prompt", ""), Map.entry("Another one", ""));
    }

    @Test
    void anEmptyMapDeletesTheFileAndACorruptOneLoadsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("t.yaml");
        Files.writeString(file, "\t not: [yaml");
        assertThat(QodoImported.load(file)).isEmpty();

        QodoImported.save(file, Map.of());
        assertThat(file).doesNotExist();
    }
}
