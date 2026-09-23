package com.contextswitcher.tasks.title;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.contextswitcher.tasks.TaskFileParser;
import com.contextswitcher.tasks.TaskFileReadWrite;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-local-title~1]
class InitialTitleAdopterTest {

    private static final String DESCRIPTION =
            "Fix the login button on Safari when an ad blocker is enabled, and check the console errors";

    private final MemoryFiles files = new MemoryFiles();
    /// The summaries "in flight": run by the test, so it can act in between.
    private final List<Runnable> background = new ArrayList<>();
    private final List<String> adopted = new ArrayList<>();

    @Test
    void adoptsTheSummaryAndKeepsTheDescriptionInTheNotes() throws Exception {
        files.contents.put("jabref/t.md", task(DESCRIPTION));

        adopter(description -> Optional.of("Fix Safari login")).adopt("jabref/t");
        runBackground();

        String content = files.contents.get("jabref/t.md");
        assertThat(new TaskFileParser().parse("jabref/t", content).title()).isEqualTo("Fix Safari login");
        assertThat(content).endsWith("# Notes\n\n" + DESCRIPTION + "\n");
        assertThat(adopted).containsExactly("jabref/t.md");
    }

    @Test
    void aTitleChangedMeanwhileIsKept() throws Exception {
        files.contents.put("t.md", task(DESCRIPTION));

        adopter(description -> Optional.of("Fix Safari login")).adopt("t");
        files.contents.put("t.md", task("My own title"));
        runBackground();

        assertThat(new TaskFileParser().parse("t", files.contents.get("t.md")).title()).isEqualTo("My own title");
        assertThat(adopted).isEmpty();
    }

    @Test
    void aShortTitleIsNotSummarized() {
        files.contents.put("t.md", task("Fix login"));
        AtomicInteger asked = new AtomicInteger();

        adopter(description -> {
            asked.incrementAndGet();
            return Optional.of("Other");
        }).adopt("t");
        runBackground();

        assertThat(asked).hasValue(0);
        assertThat(files.contents.get("t.md")).isEqualTo(task("Fix login"));
    }

    @Test
    void noTitleLeavesTheFileAlone() {
        files.contents.put("t.md", task(DESCRIPTION));

        adopter(description -> Optional.empty()).adopt("t");
        runBackground();

        assertThat(files.contents.get("t.md")).isEqualTo(task(DESCRIPTION));
        assertThat(adopted).isEmpty();
    }

    @Test
    void aThrowingSummarizerLeavesTheFileAlone() {
        files.contents.put("t.md", task(DESCRIPTION));

        adopter(description -> {
            throw new IllegalStateException("broken");
        }).adopt("t");
        runBackground();

        assertThat(files.contents.get("t.md")).isEqualTo(task(DESCRIPTION));
    }

    @Test
    void aMissingFileStartsNothing() {
        adopter(description -> Optional.of("Title")).adopt("gone");

        assertThat(background).isEmpty();
        assertThat(files.contents).isEmpty();
    }

    /// Background work is queued for [#runBackground]; the file thread runs
    /// inline, as the FX thread would run it after the queued work.
    private InitialTitleAdopter adopter(TaskTitleSummarizer summarizer) {
        return new InitialTitleAdopter(summarizer, files, background::add, Runnable::run, adopted::add);
    }

    private void runBackground() {
        List<Runnable> queued = List.copyOf(background);
        background.clear();
        queued.forEach(Runnable::run);
    }

    private static String task(String title) {
        return TaskFileParser.newTaskContent(title, null, null, false);
    }

    private static final class MemoryFiles implements TaskFileReadWrite {
        private final Map<String, String> contents = new HashMap<>();

        @Override
        public @Nullable String read(String fileName) {
            return contents.get(fileName);
        }

        @Override
        public @Nullable String save(String fileName, String content) {
            contents.put(fileName, content);
            return null;
        }
    }
}
