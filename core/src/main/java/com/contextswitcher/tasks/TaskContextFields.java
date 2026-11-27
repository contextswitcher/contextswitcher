package com.contextswitcher.tasks;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// The Add-task dialog's optional context fields — local folders, a note
/// link and a chat link — read from what the user typed or pasted, and
/// written into a new task file's frontmatter (`dsn~task-create-context-fields~1`).
// [impl->dsn~task-create-context-fields~1]
public final class TaskContextFields {

    private TaskContextFields() {
    }

    /// The folders a Folders field lists: one per non-blank line, each
    /// stripped of the double quotes Windows' *Copy as path* adds and a
    /// `file://` URL turned into its path ([#fromFileUrl]); duplicates
    /// dropped, order kept. Not checked against the disk — a folder typed
    /// for later is the user's call.
    public static List<String> folders(@Nullable String text) {
        List<String> folders = new ArrayList<>();
        if (text == null) {
            return folders;
        }
        for (String line : text.split("\\R")) {
            String folder = fromFileUrl(unquote(line.strip()));
            if (!folder.isEmpty() && !folders.contains(folder)) {
                folders.add(folder);
            }
        }
        return folders;
    }

    /// A `file://` URL as the path it names, anything else unchanged:
    /// `file:///C:/Users/me/My%20Docs` → `C:\Users\me\My Docs`,
    /// `file:///data/notes` → `/data/notes`, `file://server/share/x` →
    /// `\\server\share\x`. Explorer's address bar and browsers copy
    /// folders that way; a file manager opens only the path.
    public static String fromFileUrl(String text) {
        String url = text.strip();
        if (!url.regionMatches(true, 0, "file:", 0, 5)) {
            return text;
        }
        String rest = url.substring(5);
        String host = "";
        if (rest.startsWith("//")) {
            int slash = rest.indexOf('/', 2);
            host = slash < 0 ? rest.substring(2) : rest.substring(2, slash);
            rest = slash < 0 ? "/" : rest.substring(slash);
        }
        String path;
        try {
            path = URLDecoder.decode(rest.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            path = rest;
        }
        if (!host.isEmpty() && !host.equalsIgnoreCase("localhost")) {
            return "\\\\" + host + path.replace('/', '\\');
        }
        if (path.matches("/[A-Za-z]:(/.*)?")) {
            String drive = path.substring(1);
            return drive.length() == 2 ? drive + "\\" : drive.replace('/', '\\');
        }
        return path;
    }

    /// The note link a Note field stands for, or null when blank: the
    /// `onenote:` desktop link when the text carries one (OneNote's
    /// *Copy Link to Page* puts a web URL and the `onenote:` URL on the
    /// clipboard, and a single-line field glues them together), else the
    /// first non-blank line as typed — a web link is kept as it is, since
    /// neither form can be derived from the other.
    public static @Nullable String note(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String desktop = OneNoteLink.noteLink(text);
        return desktop != null ? desktop : firstLine(text);
    }

    /// Whether a Note field holds a OneNote *web* link only (no `onenote:`
    /// link) — it opens the browser, so the dialog hints at the desktop form.
    public static boolean isWebOnlyNote(@Nullable String text) {
        String note = note(text);
        return note != null && !note.startsWith("onenote:") && isOneNoteWebUrl(note);
    }

    /// The chat link a Chat field stands for: its first non-blank line, or
    /// null when blank. Any URL — Matrix permalinks are rewritten when the
    /// task is switched to, Teams links pass through.
    public static @Nullable String chat(@Nullable String text) {
        return text == null || text.isBlank() ? null : firstLine(text);
    }

    /// The clipboard's text as a folder of this machine, or null: one line,
    /// optionally in the double quotes Windows' *Copy as path* adds, naming an
    /// absolute path to an existing directory — say
    /// `c:\git-repositories\git.overleaf.com\6aad3ebf9a43af8873645708`.
    public static @Nullable String clipboardFolder(@Nullable String clip) {
        if (clip == null) {
            return null;
        }
        String text = unquote(clip.strip());
        if (text.isEmpty() || text.contains("\n") || text.contains("\r")) {
            return null;
        }
        try {
            Path path = Path.of(text);
            return path.isAbsolute() && Files.isDirectory(path) ? text : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /// The clipboard as a note link, or null: only when it holds nothing but
    /// links (one per line) and one of them is OneNote's — an `onenote:` link
    /// (preferred) or a OneNote web URL. A description that merely mentions a
    /// link stays a description.
    public static @Nullable String clipboardNote(@Nullable String clip) {
        if (clip == null || clip.isBlank()) {
            return null;
        }
        List<String> lines = clip.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        boolean onlyLinks = lines.stream().allMatch(line -> !line.contains(" ")
                && (line.startsWith("onenote:") || line.startsWith("https://") || line.startsWith("http://")));
        if (!onlyLinks) {
            return null;
        }
        String desktop = OneNoteLink.extract(clip);
        if (desktop != null) {
            return desktop;
        }
        return lines.size() == 1 && isOneNoteWebUrl(lines.getFirst()) ? lines.getFirst() : null;
    }

    /// The frontmatter key a task's local folders go under on the machine
    /// `host`: `folders-<host>` (`dsn~machine-key-variants~1`), since a path
    /// of this machine means nothing on the other one the task directory is
    /// synchronised with; plain `folders` when the host name is unknown.
    public static String foldersKey(String host) {
        return host.isBlank() ? "folders" : "folders-" + host.strip().toLowerCase(Locale.ROOT);
    }

    /// `content` with the filled fields written into its frontmatter: the
    /// folders under [#foldersKey] as a block list, `note:` and `chat:` as
    /// scalars. An empty field — or one equal to what the category already
    /// provides (`inherited*`), which a task falls back to anyway — writes
    /// no key.
    public static String apply(String content, List<String> folders, @Nullable String note,
            @Nullable String chat, String host, List<String> inheritedFolders,
            @Nullable String inheritedNote, @Nullable String inheritedChat) {
        String result = content;
        if (chat != null && !chat.equals(inheritedChat)) {
            result = TaskFileParser.withScalar(result, "chat", chat);
        }
        if (note != null && !note.equals(inheritedNote)) {
            result = TaskFileParser.withNote(result, note);
        }
        if (!folders.isEmpty() && !folders.equals(inheritedFolders)) {
            result = TaskFileParser.withBlockList(result, foldersKey(host), folders);
        }
        return result;
    }

    private static boolean isOneNoteWebUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://onedrive.live.com/") || lower.startsWith("https://1drv.ms/")
                || lower.startsWith("https://d.docs.live.net/")
                || (lower.startsWith("https://") && lower.contains(".sharepoint.com/")
                        && lower.contains("onenote"));
    }

    private static String firstLine(String text) {
        return text.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1).strip();
        }
        return text;
    }
}
