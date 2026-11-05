package com.contextswitcher.terminal;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-link-copy~2]
class WrappedLinkTest {

    private static final int WIDTH = 60;
    private static final String PATH =
            "/tmp/claude-1001/-data-kopp-latex/f6a35393-c599-41ae-8573-045a97dad5e8/scratchpad/guide.pdf";
    private static final String URL =
            "https://github.com/contextswitcher/contextswitcher/compare/v2026-11-02...v2026-11-03";

    /// `prefix` plus as much of `link` as fills the row, then the rest indented.
    private static List<String> wrapped(String prefix, String link) {
        int cut = WIDTH - 1 - prefix.length();
        return List.of(
                "  Some text ahead of it.",
                prefix + link.substring(0, cut),
                "  " + link.substring(cut) + ". Every flawed place",
                "");
    }

    @Test
    void joinsAPathFromEitherRow() {
        List<String> rows = wrapped("  It is at ", PATH);

        assertThat(WrappedLink.at(rows, 1, 20, WIDTH)).isEqualTo(new WrappedLink.Target(PATH, true));
        assertThat(WrappedLink.at(rows, 2, 3, WIDTH)).isEqualTo(new WrappedLink.Target(PATH, true));
    }

    @Test
    void joinsAUrl() {
        List<String> rows = wrapped("  See ", URL);

        assertThat(WrappedLink.at(rows, 2, 3, WIDTH)).isEqualTo(new WrappedLink.Target(URL, false));
    }

    @Test
    void spansMoreThanTwoRows() {
        String path = "/" + "a".repeat(55) + "/" + "b".repeat(55) + "/" + "c".repeat(20) + ".pdf";
        List<String> rows = List.of(path.substring(0, 59), "  " + path.substring(59, 116), "  " + path.substring(116));

        assertThat(WrappedLink.at(rows, 2, 4, WIDTH)).isEqualTo(new WrappedLink.Target(path, true));
    }

    @Test
    void textBesideTheLinkIsNoLink() {
        List<String> rows = wrapped("  It is at ", PATH);

        assertThat(WrappedLink.at(rows, 1, 4, WIDTH)).isNull();
        assertThat(WrappedLink.at(rows, 2, 0, WIDTH)).isNull();
        assertThat(WrappedLink.at(rows, 2, WIDTH - 1, WIDTH)).isNull();
    }

    @Test
    void aRowThatEndsEarlyContinuesNothing() {
        List<String> rows = List.of("  look in /foo/bar", "  readme.md for details");

        assertThat(WrappedLink.at(rows, 0, 12, WIDTH)).isNull();
    }
}
