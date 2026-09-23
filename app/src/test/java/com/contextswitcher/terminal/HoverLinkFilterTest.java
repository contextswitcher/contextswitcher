package com.contextswitcher.terminal;

import java.util.ArrayList;
import java.util.List;

import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem;
import com.techsenger.jeditermfx.ui.hyperlinks.LinkInfoEx;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-link-hover~1]
class HoverLinkFilterTest {

    private final List<String> reported = new ArrayList<>();

    private final IssueHyperlinkFilter issues =
            new IssueHyperlinkFilter(() -> "https://github.com/JabRef/jabref");

    private final HoverLinkFilter filter =
            new HoverLinkFilter(issues, issues::urlFor, url -> reported.add(String.valueOf(url)));

    @Test
    void reportsTheIssueUrlOnEnterAndClearsItOnExit() {
        LinkResult result = filter.apply("fixed in #17038 today");
        assertThat(result).isNotNull();

        LinkInfoEx.HoverConsumer hover =
                LinkInfoEx.getHoverConsumer(result.getItems().getFirst().getLinkInfo());
        assertThat(hover).isNotNull();

        hover.onMouseEntered(null, null);
        hover.onMouseExited();

        assertThat(reported).containsExactly("https://github.com/JabRef/jabref/issues/17038", "null");
    }

    @Test
    void keepsTheDelegatesOffsetsAndClicks() {
        List<LinkResultItem> items = filter.apply("see #42").getItems();

        assertThat(items).extracting(LinkResultItem::getStartOffset).containsExactly(4);
        assertThat(items).extracting(LinkResultItem::getEndOffset).containsExactly(7);
    }

    @Test
    void passesLinksThroughUnchangedWhenThereIsNoUrlToShow() {
        HoverLinkFilter noUrl = new HoverLinkFilter(issues, text -> null, reported::add);

        LinkResult result = noUrl.apply("see #42");

        assertThat(LinkInfoEx.getHoverConsumer(result.getItems().getFirst().getLinkInfo())).isNull();
    }

    @Test
    void staysNullWhenTheDelegateFindsNothing() {
        assertThat(filter.apply("nothing to see here")).isNull();
    }
}
