package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-add-link~3]
class TaskFileWithNoteTest {

    private final TaskFileParser parser = new TaskFileParser();

    @Test
    void insertsNoteAfterTitleAndParses() throws Exception {
        String content = """
                ---
                title: "T"
                status: active
                ---
                body
                """;

        String updated = TaskFileParser.withNote(content, "onenote:https://d.docs.live.net/x#p&end");

        Task task = parser.parse("t", updated);
        assertThat(task.note()).isEqualTo("onenote:https://d.docs.live.net/x#p&end");
        assertThat(updated).contains("body");
    }

    @Test
    void replacesAnExistingNoteLine() throws Exception {
        String content = """
                ---
                title: "T"
                note: onenote:old
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.withNote(content, "onenote:new"));

        assertThat(task.note()).isEqualTo("onenote:new");
    }
}
