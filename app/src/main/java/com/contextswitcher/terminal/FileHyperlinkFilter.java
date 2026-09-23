package com.contextswitcher.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.techsenger.jeditermfx.core.model.hyperlinks.HyperlinkFilter;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkInfo;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem;
import org.jspecify.annotations.Nullable;

/// Makes a file path in the mirrored terminal clickable — the
/// `~/.contextswitcher/attachments/img-….png` of a sent image, the report
/// Claude just wrote into its scratchpad — so opening it is a click instead
/// of a trip through the "Files" menu's "Custom path…" dialog.
///
/// Only paths that **end in an extension** count: a bare directory is nothing
/// to open, and requiring `.<ext>` keeps the slashes of ordinary prose out of
/// the links. A path Claude Code hard-wrapped across two lines cannot be
/// recovered by a line-wise filter (see `TerminalPane.showRemoteFiles`) and
/// stays plain text — the menu remains the way to reach those.
// [impl->dsn~terminal-file-links~1]
public class FileHyperlinkFilter implements HyperlinkFilter {

    /// An absolute (or `~`-rooted) path whose last segment carries an
    /// extension. The lookbehind keeps it out of a URL's path — that one is
    /// `DefaultHyperlinkFilter`'s link, and a second one over the same text
    /// would fight it.
    private static final Pattern PATH = Pattern.compile(
            "(?<![\\w:/~.-])~?(?:/[A-Za-z0-9._+@%-]+)*/[A-Za-z0-9._+@%-]*[A-Za-z0-9_-]"
                    + "\\.[A-Za-z0-9]{1,8}(?![\\w.-])");

    private final Consumer<String> onClick;

    public FileHyperlinkFilter(Consumer<String> onClick) {
        this.onClick = onClick;
    }

    @Override
    public @Nullable LinkResult apply(String line) {
        List<LinkResultItem> items = new ArrayList<>();
        Matcher matcher = PATH.matcher(line);
        while (matcher.find()) {
            String path = matcher.group();
            items.add(new LinkResultItem(matcher.start(), matcher.end(),
                    new LinkInfo(() -> onClick.accept(path))));
        }
        return items.isEmpty() ? null : new LinkResult(items);
    }

    /// The path a matched token opens, for the status-bar hover text of
    /// `dsn~terminal-link-hover~1` — the link text itself, so the hover says
    /// what a wrapped or truncated line cannot.
    public static @Nullable String pathFor(String linkText) {
        Matcher matcher = PATH.matcher(linkText);
        return matcher.find() ? matcher.group() : null;
    }
}
