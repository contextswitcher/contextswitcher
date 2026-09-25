package com.contextswitcher.queue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~message-queue-store~2]
// [utest->dsn~message-queue-read-mark~1]
class QueueFileTest {

    @TempDir
    Path dir;

    @Test
    void roundTripsMultiLineMessagesIncludingYamlLookalikes() throws IOException {
        Path file = dir.resolve("q.yaml");
        List<String> messages = List.of(
                "first message\nwith a second line",
                "---\nlooks like a YAML document separator",
                "quotes: \"double\" and 'single'");

        QueueFile.save(file, messages);

        assertThat(QueueFile.load(file)).isEqualTo(messages);
    }

    @Test
    void missingFileIsAnEmptyQueue() {
        assertThat(QueueFile.load(dir.resolve("absent.yaml"))).isEmpty();
    }

    // [utest->dsn~task-move-dnd~6]
    @Test
    void renameMovesTheQueueToTheNewTaskId() throws IOException {
        QueueFile.save(QueueFile.file(dir, "group/old-name"), List.of("pending message"));

        QueueFile.rename(dir, "group/old-name", "group/2026-07-17-new-name");

        assertThat(QueueFile.file(dir, "group/old-name")).doesNotExist();
        assertThat(QueueFile.load(QueueFile.file(dir, "group/2026-07-17-new-name")))
                .containsExactly("pending message");
        QueueFile.rename(dir, "never-existed", "wherever"); // missing queue = silent no-op
    }

    // [utest->dsn~last-sent-message~5]
    @Test
    void lastSentRoundTripsAndOverwrites() {
        Path file = QueueFile.sentFile(dir, "group/task");
        assertThat(QueueFile.loadSent(file)).isNull(); // never recorded

        QueueFile.saveSent(dir, "group/task", "first message\nwith a second line");
        QueueFile.saveSent(dir, "group/task", "the later send wins");

        assertThat(QueueFile.loadSent(file)).isEqualTo("the later send wins");
        assertThat(file).isEqualTo(dir.resolve("group__task.sent.txt"));
    }

    // [utest->dsn~last-sent-message~5]
    @Test
    void sentHistoryKeepsEveryMessageOldestFirst() {
        QueueFile.saveSent(dir, "group/task", "first");
        QueueFile.saveSent(dir, "group/task", "second");

        Path history = QueueFile.sentHistoryFile(dir, "group/task");
        assertThat(history).isEqualTo(dir.resolve("group__task.sent-history.yaml"));
        assertThat(QueueFile.load(history)).containsExactly("first", "second");
    }

    // [utest->dsn~last-sent-message~5]
    @Test
    void sentHistoryIsCappedAtTheNewestMessages() {
        for (int i = 1; i <= 55; i++) {
            QueueFile.saveSent(dir, "t", "message " + i);
        }

        List<String> history = QueueFile.load(QueueFile.sentHistoryFile(dir, "t"));
        assertThat(history).hasSize(50);
        assertThat(history.getFirst()).isEqualTo("message 6");
        assertThat(history.getLast()).isEqualTo("message 55");
    }

    // [utest->dsn~last-sent-message~5]
    @Test
    void renameMovesTheLastSentRecordToo() {
        QueueFile.saveSent(dir, "old", "what Claude works on");

        QueueFile.rename(dir, "old", "new");

        assertThat(QueueFile.sentFile(dir, "old")).doesNotExist();
        assertThat(QueueFile.sentHistoryFile(dir, "old")).doesNotExist();
        assertThat(QueueFile.loadSent(QueueFile.sentFile(dir, "new")))
                .isEqualTo("what Claude works on");
        assertThat(QueueFile.load(QueueFile.sentHistoryFile(dir, "new")))
                .containsExactly("what Claude works on");
    }

    @Test
    void corruptFileIsAnEmptyQueueNotAnException() throws IOException {
        Path file = dir.resolve("q.yaml");
        Files.writeString(file, "{ not a list");

        assertThat(QueueFile.load(file)).isEmpty();
    }

    @Test
    void savingAnEmptyQueueDeletesTheFile() throws IOException {
        Path file = dir.resolve("q.yaml");
        QueueFile.save(file, List.of("one"));

        QueueFile.save(file, List.of());

        assertThat(file).doesNotExist();
    }

    @Test
    void readMarksRoundTripAndFollowATaskRename() throws IOException {
        QueueFile.save(QueueFile.readFile(dir, "old"), List.of("done one", "done two"));

        QueueFile.rename(dir, "old", "new");

        assertThat(QueueFile.readFile(dir, "old")).doesNotExist();
        assertThat(QueueFile.load(QueueFile.readFile(dir, "new")))
                .containsExactly("done one", "done two");
    }

    // [utest->dsn~message-queue-delayed-send~4]
    @Test
    void armedMessagesRoundTripAndNothingArmedDeletesTheFile() throws IOException {
        Path file = dir.resolve("armed-messages.yaml");
        Map<String, List<String>> armed = Map.of("group/task", List.of("first\nline two", "---"));

        QueueFile.saveArmed(file, armed);
        assertThat(QueueFile.loadArmed(file)).isEqualTo(armed);

        QueueFile.saveArmed(file, Map.of());
        assertThat(file).doesNotExist();
        assertThat(QueueFile.loadArmed(file)).isEmpty();
    }

    @Test
    void groupedTaskIdsMapToOneFlatFile() {
        assertThat(QueueFile.file(Path.of("queues"), "jabref/fix-npe"))
                .isEqualTo(Path.of("queues", "jabref__fix-npe.yaml"));
    }
}
