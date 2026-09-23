package com.contextswitcher.switching;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~note-focus-action~1]
class NoteFocusActionTest {

    private static Task task(String id, @Nullable String note) {
        return new Task(id, "T", TaskStatus.ACTIVE, null, null, null, null, null, null, note, null, "");
    }

    /// A resolver over a fixed group→note map, standing in for reading
    /// `CONTEXTSWITCHER.md`.
    private static NoteFocusAction action(AtomicReference<String> opened, Map<String, String> groupNotes) {
        return new NoteFocusAction(opened::set, groupNotes::get);
    }

    @Test
    void notConfiguredWithoutTaskOrCategoryNote() {
        NoteFocusAction action = action(new AtomicReference<>(), Map.of());
        assertThat(action.isConfigured(task("jabref/fix", null))).isFalse();
    }

    @Test
    void configuredFromTaskNote() {
        NoteFocusAction action = action(new AtomicReference<>(), Map.of());
        assertThat(action.isConfigured(task("jabref/fix", "onenote:x"))).isTrue();
    }

    @Test
    void configuredFromCategoryNoteWhenTaskHasNone() {
        NoteFocusAction action = action(new AtomicReference<>(), Map.of("jabref", "onenote:group"));
        assertThat(action.isConfigured(task("jabref/fix", null))).isTrue();
    }

    @Test
    void rootLevelTaskHasNoCategoryNote() {
        NoteFocusAction action = action(new AtomicReference<>(), Map.of("", "onenote:root"));
        assertThat(action.isConfigured(task("loose", null))).isFalse();
    }

    @Test
    void opensTheTaskNoteInPreferenceToTheCategory() {
        AtomicReference<String> opened = new AtomicReference<>();
        NoteFocusAction action = action(opened, Map.of("jabref", "onenote:group"));
        ActionResult result = action.run(task("jabref/fix", "onenote:task"));
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("opening task note");
        assertThat(opened.get()).isEqualTo("onenote:task");
    }

    @Test
    void opensTheCategoryNoteAsFallback() {
        AtomicReference<String> opened = new AtomicReference<>();
        NoteFocusAction action = action(opened, Map.of("jabref", "onenote:group"));
        ActionResult result = action.run(task("jabref/fix", null));
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("opening category note");
        assertThat(opened.get()).isEqualTo("onenote:group");
    }

    @Test
    void failsWhenNoNoteResolves() {
        NoteFocusAction action = action(new AtomicReference<>(), Map.of());
        ActionResult result = action.run(task("jabref/fix", null));
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("no note configured");
    }

    @Test
    void failsWhenTheOpenerThrows() {
        NoteFocusAction action = new NoteFocusAction(url -> {
            throw new RuntimeException("boom");
        }, group -> null);
        ActionResult result = action.run(task("jabref/fix", "onenote:task"));
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("boom");
    }
}
