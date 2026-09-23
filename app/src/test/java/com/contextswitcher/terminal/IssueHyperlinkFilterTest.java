package com.contextswitcher.terminal;

import java.util.List;

import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-issue-links~2]
class IssueHyperlinkFilterTest {

    private static final IssueHyperlinkFilter WITH_REPO =
            new IssueHyperlinkFilter(() -> "https://github.com/JabRef/jabref");

    @Test
    void marksEveryIssueNumberInTheLine() {
        LinkResult result = WITH_REPO.apply("- #17038 is the unblocker, then #17019 merges.");

        assertThat(result).isNotNull();
        assertThat(result.getItems()).extracting(LinkResultItem::getStartOffset).containsExactly(2, 32);
        assertThat(result.getItems()).extracting(LinkResultItem::getEndOffset).containsExactly(8, 38);
    }

    @Test
    void ignoresNumbersThatArePartOfALongerToken() {
        assertThat(WITH_REPO.apply("color #1a2b3c on pane#2 of run#7x")).isNull();
    }

    @Test
    void marksPrNumbersWrittenWithoutAHash() {
        LinkResult result = WITH_REPO.apply("CI failures on PR 17039 (markdown fix)");

        assertThat(result).isNotNull();
        assertThat(result.getItems()).extracting(LinkResultItem::getStartOffset).containsExactly(15);
        assertThat(result.getItems()).extracting(LinkResultItem::getEndOffset).containsExactly(23);
    }

    @Test
    void linksNothingWithoutARepository() {
        assertThat(new IssueHyperlinkFilter(() -> null).apply("see #17038")).isNull();
    }

    @Test
    void resolvesAnUnqualifiedNumberAgainstTheMirroredRepository() {
        assertThat(WITH_REPO.urlFor("#17038")).isEqualTo("https://github.com/JabRef/jabref/issues/17038");
    }

    @Test
    void resolvesAShortPrefixAgainstAKnownRepository() {
        IssueHyperlinkFilter filter = new IssueHyperlinkFilter(
                () -> "https://github.com/contextswitcher/contextswitcher",
                () -> List.of("https://github.com/JabRef/jabref"));

        assertThat(filter.urlFor("JabRef#17103"))
                .isEqualTo("https://github.com/JabRef/jabref/issues/17103");
    }

    @Test
    void marksTheWholePrefixedToken() {
        LinkResult result = WITH_REPO.apply("traced against the actual PR (JabRef#17103, the one)");

        assertThat(result).isNotNull();
        assertThat(result.getItems()).extracting(LinkResultItem::getStartOffset).containsExactly(30);
        assertThat(result.getItems()).extracting(LinkResultItem::getEndOffset).containsExactly(42);
    }

    @Test
    void resolvesAQualifiedPrefixOnTheForgeOfTheMirroredRepository() {
        IssueHyperlinkFilter filter =
                new IssueHyperlinkFilter(() -> "https://gitlab.example.com/team/app");

        assertThat(filter.urlFor("other/lib#7"))
                .isEqualTo("https://gitlab.example.com/other/lib/issues/7");
    }

    @Test
    void linksNoPrefixThatNamesNoKnownRepository() {
        assertThat(WITH_REPO.apply("the label on pane#2")).isNull();
    }
}
