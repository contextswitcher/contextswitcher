package com.contextswitcher.tasks;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~browser-url-title~1]
class TaskFileBrowserTitleTest {

    private final TaskFileParser parser = new TaskFileParser();

    @Test
    void parsesScalarAndMapEntriesInOneList() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://plain.example/
                    - url: https://moodle.example/quiz
                      title: Probeklausur in moodle
                ---
                """;

        Task task = parser.parse("t", content);

        assertThat(task.browser().entries()).containsExactly(
                new Task.UrlEntry("https://plain.example", null),
                new Task.UrlEntry("https://moodle.example/quiz", "Probeklausur in moodle"));
        assertThat(task.browser().urls())
                .containsExactly("https://plain.example", "https://moodle.example/quiz");
    }

    /// The forms are what matters here; the entries come out in sorted order
    /// (`moodle` before `plain`), not in the order they were added.
    @Test
    void addsScalarWithoutTitleAndMapWithTitle() throws Exception {
        String content = """
                ---
                title: "T"
                ---
                body
                """;

        String withPlain = TaskFileParser.addBrowserUrl(content, "https://plain.example/", null);
        String withTitled =
                TaskFileParser.addBrowserUrl(withPlain, "https://moodle.example/quiz", "Probeklausur in moodle");

        Task task = parser.parse("t", withTitled);
        assertThat(task.browser().entries()).containsExactly(
                new Task.UrlEntry("https://moodle.example/quiz", "Probeklausur in moodle"),
                new Task.UrlEntry("https://plain.example", null));
        assertThat(withTitled).contains("url: https://moodle.example/quiz");
        assertThat(withTitled).contains("title: Probeklausur in moodle");
    }

    @Test
    void blankTitleFallsBackToScalarForm() throws Exception {
        String content = """
                ---
                title: "T"
                ---
                """;

        String updated = TaskFileParser.addBrowserUrl(content, "https://plain.example/", "   ");

        assertThat(parser.parse("t", updated).browser().entries())
                .containsExactly(new Task.UrlEntry("https://plain.example", null));
        assertThat(updated).doesNotContain("url:");
    }

    @Test
    void ofUrlsBuildsUntitledEntries() {
        assertThat(Task.BrowserConfig.ofUrls("https://a/", "https://b/").entries())
                .containsExactly(new Task.UrlEntry("https://a/", null), new Task.UrlEntry("https://b/", null));
        assertThat(Task.BrowserConfig.ofUrls().entries()).isEqualTo(List.of());
    }
}
