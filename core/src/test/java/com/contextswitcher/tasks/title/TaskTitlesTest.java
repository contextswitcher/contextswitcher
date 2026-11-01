package com.contextswitcher.tasks.title;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-local-title~2]
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

    @Test
    void aModelAnswerThatIsATitleIsSanitized() {
        assertThat(TaskTitles.fromModelAnswer("\"Set up ungerts-jev workspace.\"\n"))
                .contains("Set up ungerts-jev workspace");
    }

    /// Field report 2026-10-31: a description that was itself a prompt got
    /// the title `<invoke_tool>` — haiku, given no tools, tried to act.
    @Test
    void aModelAnswerWithAMarkupTagIsNoTitle() {
        assertThat(TaskTitles.fromModelAnswer("<invoke_tool>")).isEmpty();
        assertThat(TaskTitles.fromModelAnswer("<function_calls>\n<invoke name=\"Write\">")).isEmpty();
        assertThat(TaskTitles.fromModelAnswer("</tool_use>")).isEmpty();
    }

    @Test
    void aModelAnswerThatIsASentenceIsNoTitle() {
        assertThat(TaskTitles.fromModelAnswer("I'll set up the folder and write a CLAUDE.md file for it now"))
                .isEmpty();
    }

    @Test
    void aModelAnswerMayStillCompareWithAngleBrackets() {
        assertThat(TaskTitles.fromModelAnswer("Handle a < b in sort")).contains("Handle a < b in sort");
    }
}
