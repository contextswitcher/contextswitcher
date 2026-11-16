package com.contextswitcher.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// [utest->dsn~todo-txt-line~1]
class TodoTxtTest {

    @Test
    void openLinesWithACsTagAreRenderedByTask() {
        Map<String, List<TodoTxt.Line>> byTask = TodoTxt.byTask(List.of(
                "(B) 2026-11-01 Review the comments +JabRef @desk cs:jabref/fix-npe due:2026-11-20 t:2026-11-18 #review see https://example.org/x",
                "x 2026-11-02 Done already cs:jabref/fix-npe",
                "Plain line without a task",
                "Name it cs:My%20Category/a+b"));

        assertThat(byTask).containsOnlyKeys("jabref/fix-npe", "My Category/a+b");
        assertThat(byTask.get("jabref/fix-npe")).containsExactly(new TodoTxt.Line(
                "(B) Review the comments see https://example.org/x",
                List.of("+JabRef", "@desk", "due 2026-11-20", "#review")));
        assertThat(byTask.get("My Category/a+b")).containsExactly(new TodoTxt.Line("Name it", List.of()));
    }

    @Test
    void theFileIsReadAgainWhenItChanges(@TempDir Path dir) throws Exception {
        Path todo = dir.resolve("todo.txt");
        Files.writeString(todo, "First cs:a\n");
        TodoTxt.File file = new TodoTxt.File();
        assertThat(file.linesFor("a")).isEmpty();

        file.setPath(todo.toString());
        assertThat(file.linesFor("a")).extracting(TodoTxt.Line::text).containsExactly("First");

        Files.writeString(todo, "Second cs:a\n");
        Files.setLastModifiedTime(todo, java.nio.file.attribute.FileTime.fromMillis(0));
        assertThat(file.linesFor("a")).extracting(TodoTxt.Line::text).containsExactly("Second");

        Files.delete(todo);
        assertThat(file.linesFor("a")).isEmpty();
    }
}
