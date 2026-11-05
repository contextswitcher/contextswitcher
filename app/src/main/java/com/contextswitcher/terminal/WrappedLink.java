package com.contextswitcher.terminal;

import java.util.List;

import com.techsenger.jeditermfx.core.model.hyperlinks.HyperlinkFilter;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem;
import com.techsenger.jeditermfx.ui.DefaultHyperlinkFilter;
import org.jspecify.annotations.Nullable;

/// Finds the URL or file path Claude Code hard-wrapped across terminal rows.
///
/// Such a link is several unrelated lines to the terminal, each with its own
/// indentation, so the line-wise filters see at most a cut-off piece of it.
/// This glues the rows back together around a position in them.
// [impl->dsn~terminal-link-copy~2]
public final class WrappedLink {

    /// How far short of the last column a hard-cut row may end.
    // ponytail: Claude's wrap width is not known here, so a complete link that
    // ends this close to the edge swallows the next row's first word.
    private static final int EDGE_MARGIN = 4;

    /// A link's full text, and whether it is a file path rather than a URL.
    public record Target(String text, boolean path) {
    }

    private WrappedLink() {
    }

    /// The link covering `column` of `rows[row]` in a terminal `width` columns
    /// wide; null if that spot is on no link wrapped across rows.
    public static @Nullable Target at(List<String> rows, int row, int column, int width) {
        int first = row;
        while (first > 0 && continues(rows.get(first - 1), rows.get(first), width)) {
            first--;
        }
        int last = row;
        while (last + 1 < rows.size() && continues(rows.get(last), rows.get(last + 1), width)) {
            last++;
        }
        if (first == last) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        int offset = -1;
        for (int i = first; i <= last; i++) {
            String text = rows.get(i).stripTrailing();
            int indent = i == first ? 0 : text.length() - text.stripLeading().length();
            if (i == row) {
                if (column < indent || column >= text.length()) {
                    return null;
                }
                offset = joined.length() + column - indent;
            }
            joined.append(text, indent, text.length());
        }
        String line = joined.toString();
        Target url = find(new DefaultHyperlinkFilter(), line, offset, false);
        return url != null ? url : find(new FileHyperlinkFilter(path -> { }), line, offset, true);
    }

    /// Whether `next` carries on what the hard-cut `row` stopped in the middle of.
    private static boolean continues(String row, String next, int width) {
        String text = row.stripTrailing();
        return text.length() >= width - EDGE_MARGIN && !text.isEmpty() && !next.isBlank();
    }

    private static @Nullable Target find(HyperlinkFilter filter, String line, int offset, boolean path) {
        LinkResult result = filter.apply(line);
        if (result == null) {
            return null;
        }
        for (LinkResultItem item : result.getItems()) {
            if (item.getStartOffset() <= offset && offset < item.getEndOffset()) {
                return new Target(line.substring(item.getStartOffset(), item.getEndOffset()), path);
            }
        }
        return null;
    }
}
