package com.contextswitcher.tasks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// Extracts the desktop-app link from a OneNote "Copy Link to Page"
/// clipboard, which carries two lines: a `https://onedrive.live.com/…` web
/// URL (opens the browser) and an `onenote:…` URL (opens the desktop app).
/// The `onenote:` form is preferred for the note link.
// [impl->dsn~task-add-link~3]
public final class OneNoteLink {

    private static final Pattern ONENOTE = Pattern.compile("onenote:\\S+");

    private OneNoteLink() {
    }

    /// The `onenote:` link found anywhere in `clipboard`, or null when none
    /// is present (e.g. a plain browser URL was copied).
    public static @Nullable String extract(String clipboard) {
        Matcher matcher = ONENOTE.matcher(clipboard);
        return matcher.find() ? matcher.group() : null;
    }

    /// The note link a typed or pasted `text` stands for: the whole text when it
    /// is an `onenote:` link itself (a page link can carry spaces the token match
    /// would cut), else the `onenote:` link found in it, else null (a browser URL).
    public static @Nullable String noteLink(String text) {
        String link = text.strip();
        return link.startsWith("onenote:") ? link : extract(link);
    }
}
