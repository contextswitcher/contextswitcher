package com.contextswitcher.terminal;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.techsenger.jeditermfx.core.model.hyperlinks.HyperlinkFilter;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkInfo;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem;
import com.techsenger.jeditermfx.ui.hyperlinks.LinkInfoEx;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import org.jspecify.annotations.Nullable;

/// Wraps a hyperlink filter so resting the pointer on one of its links reports
/// the URL it would open, and leaving it clears the report again.
///
/// The terminal shows link *text*: `#17071` says nothing about which
/// repository it resolves against, and a wrapped URL is cut by the pane's
/// width. The hover text is the only place the target is spelled out.
// [impl->dsn~terminal-link-hover~1]
public class HoverLinkFilter implements HyperlinkFilter {

    private final HyperlinkFilter delegate;
    private final Function<String, @Nullable String> urlOf;
    private final Consumer<@Nullable String> hover;

    /// `urlOf` maps a link's text to the URL to report; a null answer leaves
    /// that link as the delegate built it (no hover text).
    public HoverLinkFilter(HyperlinkFilter delegate, Function<String, @Nullable String> urlOf,
            Consumer<@Nullable String> hover) {
        this.delegate = delegate;
        this.urlOf = urlOf;
        this.hover = hover;
    }

    @Override
    public @Nullable LinkResult apply(String line) {
        LinkResult result = delegate.apply(line);
        if (result == null) {
            return null;
        }
        List<LinkResultItem> items = result.getItems().stream()
                .map(item -> withHover(item, line))
                .toList();
        return new LinkResult(items);
    }

    private LinkResultItem withHover(LinkResultItem item, String line) {
        int start = Math.max(0, Math.min(item.getStartOffset(), line.length()));
        int end = Math.max(start, Math.min(item.getEndOffset(), line.length()));
        String url = urlOf.apply(line.substring(start, end));
        if (url == null) {
            return item;
        }
        LinkInfo original = item.getLinkInfo();
        return new LinkResultItem(item.getStartOffset(), item.getEndOffset(),
                new LinkInfoEx.Builder()
                        .setNavigateCallback(original::navigate)
                        .setHoverConsumer(new LinkInfoEx.HoverConsumer() {

                            @Override
                            public void onMouseEntered(Canvas canvas, Rectangle2D bounds) {
                                hover.accept(url);
                            }

                            @Override
                            public void onMouseExited() {
                                hover.accept(null);
                            }
                        })
                        .build());
    }
}
