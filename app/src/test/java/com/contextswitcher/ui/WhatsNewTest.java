package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~whats-new-upstream~7]
class WhatsNewTest {

    private static final String OLD = "a".repeat(40);
    private static final String THEIRS = "b".repeat(40);
    private static final String MINE = "c".repeat(40);

    private static final String NONE = "0".repeat(40);

    /// `--line-porcelain` for `text`, line `i` written by `authors[i]` ("sha name mail").
    private static List<String> porcelain(List<String> text, String... authors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.size(); i++) {
            String[] a = authors[i].split(" ");
            sb.append(a[0]).append(' ').append(i + 1).append(' ').append(i + 1).append('\n')
                    .append("author ").append(a[1]).append('\n')
                    .append("author-mail <").append(a[2]).append(">\n")
                    .append('\t').append(text.get(i)).append('\n');
        }
        return sb.toString().lines().toList();
    }

    private static WhatsNew.Source source(List<String> text, String me, String... authors) {
        return WhatsNew.parse(porcelain(text, authors), "me@example.org", me);
    }

    /// Only bullets whose first line a fresh commit wrote are news; each keeps
    /// its day and heading, takes its continuation lines, and is "me" by the
    /// author mail — case-insensitively — or else named after its author.
    @Test
    void projectsTheFreshBulletsWithDayHeadingAndAuthor() {
        List<String> changelog = List.of(
                "# Changelog",
                "## [2026-09-12] - 2026-09-12",
                "### Added",
                "- **Theirs** landed.",
                "  With a second line.",
                "- Mine landed.",
                "### Fixed",
                "- An old one.",
                "## [2026-09-11] - 2026-09-11",
                "### Changed",
                "- Old too.");
        String old = OLD + " Old x@y";
        String theirs = THEIRS + " Them them@example.org";
        String mine = MINE + " Me Me@Example.org";
        WhatsNew.Source source = source(changelog, WhatsNew.ME,
                old, old, old, theirs, mine, mine, old, old, old, old, old);

        List<WhatsNew.Item> items = WhatsNew.items(source, Set.of(THEIRS, MINE));

        assertThat(items).containsExactly(
                new WhatsNew.Item("Them", "2026-09-12", "Added", List.of("**Theirs** landed.", "With a second line.")),
                new WhatsNew.Item("me", "2026-09-12", "Added", List.of("Mine landed.")));
        assertThat(WhatsNew.plainText(items)).isEqualTo("""
                ═══ Changes by Them ═══

                2026-09-12
                  Added
                    - Theirs landed.
                      With a second line.

                ═══ Changes by me ═══

                2026-09-12
                  Added
                    - Mine landed.
                """);
    }

    /// Pending = the bullets of every source the announced copy lacks, once
    /// each even when two sources hold it (local attribution wins); a fetched
    /// bullet of my own mail is "me — remotely"; announcing makes them old for
    /// both triggers; no copy yet means nothing is pending.
    @Test
    void shortensLongAuthorNamesForTheTooltip() {
        assertThat(WhatsNew.shortBy("me")).isEqualTo("me");
        assertThat(WhatsNew.shortBy("Oliver Kopp")).isEqualTo("Oliver Kopp");
        assertThat(WhatsNew.shortBy("Carl Christian Snethlage")).isEqualTo("Carl Christ\u2026");
    }

    @Test
    void pendingIsWhatTheAnnouncedCopyLacks(@TempDir Path dir) throws Exception {
        Path copy = dir.resolve("announced.md");
        List<String> old = List.of("## [2026-09-11] - 2026-09-11", "### Added", "- Old.");
        List<String> local = List.of("## [2026-09-12] - 2026-09-12", "### Added", "- **Local** edit.",
                "  Uncommitted.", "## [2026-09-11] - 2026-09-11", "### Added", "- Old.");
        List<String> upstream = List.of("## [2026-09-12] - 2026-09-12", "### Added", "- Pushed.",
                "- Pushed elsewhere.", "- **Local** edit.", "  Uncommitted.",
                "## [2026-09-11] - 2026-09-11", "### Added", "- Old.");
        String o = OLD + " Old x@y";
        String none = NONE + " Not not.committed.yet";
        WhatsNew.Source localSource = source(local, WhatsNew.ME, o, o, none, none, o, o, o);
        String t = THEIRS + " Them them@example.org";
        String m = MINE + " Me me@example.org";
        WhatsNew.Source upstreamSource = source(upstream, WhatsNew.ME_REMOTELY, o, o, t, m, t, t, o, o, o);

        assertThat(WhatsNew.pending(copy, Map.of(true, localSource))).isEmpty();
        WhatsNew.announce(copy, Map.of(true, source(old, WhatsNew.ME, o, o, o)));
        Map<Boolean, WhatsNew.Source> sources = Map.of(true, localSource, false, upstreamSource);
        List<WhatsNew.Item> pending = WhatsNew.pending(copy, sources);
        assertThat(pending).containsExactlyInAnyOrder(
                new WhatsNew.Item("me", "2026-09-12", "Added", List.of("**Local** edit.", "Uncommitted.")),
                new WhatsNew.Item("Them", "2026-09-12", "Added", List.of("Pushed.")),
                new WhatsNew.Item("me — remotely", "2026-09-12", "Added", List.of("Pushed elsewhere.")));
        assertThat(WhatsNew.plainText(pending)).containsSubsequence(
                "Changes by Them", "Changes by me — remotely", "Changes by me ═══");
        WhatsNew.announce(copy, sources);
        assertThat(WhatsNew.pending(copy, sources)).isEmpty();
        assertThat(Files.readAllLines(copy)).contains("- Pushed.", "- **Local** edit.");
    }
}
