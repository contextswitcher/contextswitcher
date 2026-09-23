package com.contextswitcher.tasks;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-tag-model~2]
class TaskFileWithTagsTest {

    private final TaskFileParser parser = new TaskFileParser();

    @Test
    void insertsTagsAfterTitleAndParses() throws Exception {
        String content = """
                ---
                title: "T"
                status: active
                ---
                body
                """;

        Task task = parser.parse("t", TaskFileParser.withTags(content, List.of("phone", "jabref")));

        assertThat(task.tags()).containsExactly("phone", "jabref");
    }

    @Test
    void preservesTheBody() {
        String content = """
                ---
                title: "T"
                ---
                body
                """;

        assertThat(TaskFileParser.withTags(content, List.of("phone"))).contains("body");
    }

    @Test
    void replacesAnExistingTagsLine() throws Exception {
        String content = """
                ---
                title: "T"
                tags: [old]
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.withTags(content, List.of("phone")));

        assertThat(task.tags()).containsExactly("phone");
    }

    @Test
    void removingAllTagsDropsTheLine() {
        String content = """
                ---
                title: "T"
                tags: [phone]
                ---
                """;

        assertThat(TaskFileParser.withTags(content, List.of())).doesNotContain("tags:");
    }
}
