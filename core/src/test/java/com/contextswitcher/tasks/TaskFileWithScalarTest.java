package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-add-link~3]
class TaskFileWithScalarTest {

    private final TaskFileParser parser = new TaskFileParser();

    @Test
    void insertsAndReplacesKeepingComments() throws Exception {
        String content = """
                ---
                title: "T"
                # tags: [x]
                chat: https://matrix.to/#/!old:matrix.org
                ---
                body
                """;

        String updated = TaskFileParser.withScalar(content, "remote", "devbox");
        updated = TaskFileParser.withScalar(updated, "chat", "https://matrix.to/#/!new:matrix.org");

        Task task = parser.parse("t", updated);
        assertThat(task.remote()).isEqualTo("devbox");
        assertThat(task.chat()).isEqualTo("https://matrix.to/#/!new:matrix.org");
        assertThat(updated).contains("# tags: [x]").contains("body");
    }

    @Test
    void leavesContentWithoutFenceAlone() {
        assertThat(TaskFileParser.withScalar("just notes\n", "chat", "x")).isEqualTo("just notes\n");
    }
}
