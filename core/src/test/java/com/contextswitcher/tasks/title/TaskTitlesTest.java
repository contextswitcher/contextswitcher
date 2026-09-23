package com.contextswitcher.tasks.title;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-local-title~1]
class TaskTitlesTest {

    @Test
    void aShortTitleIsNoDescription() {
        assertThat(TaskTitles.looksLikeDescription("Fix the login button")).isFalse();
    }

    @Test
    void aSecondLineMakesADescription() {
        assertThat(TaskTitles.looksLikeDescription("Fix login\nand logout")).isTrue();
    }

    @Test
    void moreThanEightWordsMakeADescription() {
        assertThat(TaskTitles.looksLikeDescription("one two three four five six seven eight")).isFalse();
        assertThat(TaskTitles.looksLikeDescription("one two three four five six seven eight nine")).isTrue();
    }

    @Test
    void moreThanSixtyCharactersMakeADescription() {
        assertThat(TaskTitles.looksLikeDescription("a".repeat(60))).isFalse();
        assertThat(TaskTitles.looksLikeDescription("a".repeat(61))).isTrue();
    }

    @Test
    void sanitizeKeepsTheFirstNonBlankLineWithoutQuotesOrFinalDot() {
        assertThat(TaskTitles.sanitize("\n  \"Fix Safari login.\"  \nHere is why"))
                .contains("Fix Safari login");
        assertThat(TaskTitles.sanitize("\"Fix Safari login\".")).contains("Fix Safari login");
    }

    @Test
    void sanitizeDropsMarkdownEmphasisAndCollapsesWhitespace() {
        assertThat(TaskTitles.sanitize("**Fix   the  login**")).contains("Fix the login");
    }

    @Test
    void sanitizeKeepsAnApostropheInside() {
        assertThat(TaskTitles.sanitize("Don't lose queued messages")).contains("Don't lose queued messages");
    }

    @Test
    void sanitizeCutsALongAnswerAtAWordBoundary() {
        String title = TaskTitles.sanitize("word ".repeat(20)).orElseThrow();
        assertThat(title).hasSizeLessThanOrEqualTo(TaskTitles.MAX_LENGTH).endsWith("word");
    }

    @Test
    void sanitizeOfNothingIsEmpty() {
        assertThat(TaskTitles.sanitize(" \n\t")).isEmpty();
        assertThat(TaskTitles.sanitize("\"...\"")).isEmpty();
    }
}
