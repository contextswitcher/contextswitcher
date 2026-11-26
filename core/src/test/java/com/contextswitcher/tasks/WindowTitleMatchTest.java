package com.contextswitcher.tasks;

import java.util.List;

import com.contextswitcher.tasks.WindowTitleMatch.Candidate;
import com.contextswitcher.tasks.WindowTitleMatch.Kind;
import com.contextswitcher.tasks.WindowTitleMatch.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~switch-by-window-title~1]
class WindowTitleMatchTest {

    private static final String TEAMS = "Thesis weekly sync | Microsoft Teams";

    @Test
    void substringMatchesCaseInsensitivelyWhateverTheAppAppends() {
        assertThat(WindowTitleMatch.score(TEAMS, List.of("thesis WEEKLY sync"))).isEqualTo(18);
        assertThat(WindowTitleMatch.score(TEAMS, List.of("Other meeting", "weekly"))).isEqualTo(6);
        assertThat(WindowTitleMatch.score(TEAMS, List.of("Other meeting"))).isZero();
    }

    @Test
    void blankEntriesNeverMatch() {
        assertThat(WindowTitleMatch.score(TEAMS, List.of("", "  "))).isZero();
    }

    @Test
    void regexCharactersAreLiteral() {
        assertThat(WindowTitleMatch.score("Review (draft) | Microsoft Teams", List.of("Review (draft)")))
                .isPositive();
        assertThat(WindowTitleMatch.score("Review x | Microsoft Teams", List.of("Review .")))
                .isZero();
    }

    @Test
    void oneTaskMatching() {
        Outcome outcome = WindowTitleMatch.resolve(TEAMS,
                List.of(new Candidate("a", List.of("Budget")), new Candidate("thesis/sync", List.of("Thesis weekly"))),
                List.of());
        assertThat(outcome).isEqualTo(new Outcome(Kind.TASK, List.of("thesis/sync")));
    }

    @Test
    void longerEntryIsMoreSpecificAndWins() {
        Outcome outcome = WindowTitleMatch.resolve(TEAMS,
                List.of(new Candidate("broad", List.of("Thesis")), new Candidate("exact", List.of("Thesis weekly sync"))),
                List.of());
        assertThat(outcome).isEqualTo(new Outcome(Kind.TASK, List.of("exact")));
    }

    @Test
    void equallyGoodTasksAreSeveralInCandidateOrder() {
        Outcome outcome = WindowTitleMatch.resolve(TEAMS,
                List.of(new Candidate("b", List.of("weekly sync")), new Candidate("a", List.of("WEEKLY SYNC"))),
                List.of());
        assertThat(outcome).isEqualTo(new Outcome(Kind.TASKS, List.of("b", "a")));
    }

    @Test
    void aTaskBeatsALongerCategoryEntry() {
        Outcome outcome = WindowTitleMatch.resolve(TEAMS,
                List.of(new Candidate("t", List.of("sync"))),
                List.of(new Candidate("thesis", List.of("Thesis weekly sync"))));
        assertThat(outcome).isEqualTo(new Outcome(Kind.TASK, List.of("t")));
    }

    @Test
    void categoryCountsOnlyWithoutTaskMatch() {
        assertThat(WindowTitleMatch.resolve(TEAMS, List.of(new Candidate("t", List.of("Budget"))),
                List.of(new Candidate("thesis", List.of("Thesis")))))
                .isEqualTo(new Outcome(Kind.CATEGORY, List.of("thesis")));
        assertThat(WindowTitleMatch.resolve(TEAMS, List.of(),
                List.of(new Candidate("x", List.of("Thesis")), new Candidate("y", List.of("thesis")))))
                .isEqualTo(new Outcome(Kind.CATEGORIES, List.of("x", "y")));
    }

    @Test
    void nothingMatchesOrNoTitle() {
        List<Candidate> tasks = List.of(new Candidate("t", List.of("Thesis")));
        assertThat(WindowTitleMatch.resolve("Inbox - Outlook", tasks, List.of()).kind()).isEqualTo(Kind.NONE);
        assertThat(WindowTitleMatch.resolve("  ", tasks, List.of()).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void frontmatterReadsAStringOrAList() {
        assertThat(TaskFileParser.frontmatterStrings("---\nwindowTitle: Thesis weekly sync\n---\n", "windowTitle"))
                .containsExactly("Thesis weekly sync");
        assertThat(TaskFileParser.frontmatterStrings(
                "---\nwindowTitle:\n  - Thesis\n  - ''\n  - Budget review \n---\n", "windowTitle"))
                .containsExactly("Thesis", "Budget review");
        assertThat(TaskFileParser.frontmatterStrings("---\ntitle: x\n---\n", "windowTitle")).isEmpty();
        assertThat(TaskFileParser.frontmatterStrings("no frontmatter", "windowTitle")).isEmpty();
    }
}
