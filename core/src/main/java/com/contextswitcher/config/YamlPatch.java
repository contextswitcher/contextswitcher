package com.contextswitcher.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

/// Surgically sets a single top-level key in a YAML document's raw text,
/// preserving every other line — comments, blank lines, key order, and keys the
/// application does not know about. The settings editor edits the raw
/// `settings.yaml` as the source of truth and only ever patches the one key a
/// form field changed, so a hand-added comment or a future key survives a form
/// edit that a full re-dump would drop.
///
/// Only top-level keys are handled (settings is a flat mapping). The replacement
/// value is rendered by SnakeYAML exactly as [AppSettings#dump()] would emit it
/// (scalars, list blocks, list-of-maps for tags), so the patched document parses
/// identically to a freshly stored one.
// [impl->dsn~settings-editor~4]
public final class YamlPatch {

    /// A line that starts a new top-level key (`key:` at column 0). Terminates
    /// the block of the key being replaced. `-` and `.` belong to the key
    /// character set: a frontmatter key can carry a machine suffix
    /// (`folders-windows`), and a key the pattern does not recognise would be
    /// swallowed into — and deleted with — the block above it. A column-0 list
    /// item (`- https://…`, `- name: phone`) still does not match, since the
    /// `-` is followed by a space rather than the key's colon.
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^[A-Za-z0-9_.-]+\\s*:.*");
    /// A comment at column 0 — also terminates a block, so a comment sitting
    /// between two keys stays attached to what follows it, not to what precedes.
    private static final Pattern TOP_LEVEL_COMMENT = Pattern.compile("^#.*");

    private YamlPatch() {
    }

    /// Returns `doc` with top-level `key` set to `value` (rendered as YAML): the
    /// existing key's whole block is replaced in place, or — when the key is
    /// absent — the rendered `key: value` is appended. Everything else is left
    /// byte-for-byte untouched.
    public static String set(String doc, String key, Object value) {
        List<String> rendered = renderBlock(key, value);
        // Split keeping the structure; a trailing newline yields no phantom line.
        List<String> lines = new ArrayList<>(List.of(doc.split("\n", -1)));
        int start = indexOfKey(lines, key);
        if (start < 0) {
            return appended(doc, rendered);
        }
        int end = blockEnd(lines, start);
        List<String> out = new ArrayList<>(lines.subList(0, start));
        out.addAll(rendered);
        out.addAll(lines.subList(end, lines.size()));
        return String.join("\n", out);
    }

    /// Returns `doc` with top-level `key` and its whole block deleted, or `doc`
    /// unchanged when the key is not there. Everything else is left
    /// byte-for-byte untouched — the removal counterpart of [#set], for a form
    /// field the user cleared (an empty field means "no such key", not
    /// `key: ''`).
    public static String remove(String doc, String key) {
        List<String> lines = new ArrayList<>(List.of(doc.split("\n", -1)));
        int start = indexOfKey(lines, key);
        if (start < 0) {
            return doc;
        }
        List<String> out = new ArrayList<>(lines.subList(0, start));
        out.addAll(lines.subList(blockEnd(lines, start), lines.size()));
        return String.join("\n", out);
    }

    /// Renders `key: value` the way SnakeYAML dumps a one-key mapping — the same
    /// styling [AppSettings#dump()] uses — split into lines with no trailing blank.
    private static List<String> renderBlock(String key, Object value) {
        // singletonMap tolerates a null value; Map.of would not.
        String dumped = new Yaml().dumpAsMap(Collections.singletonMap(key, value)).stripTrailing();
        return new ArrayList<>(List.of(dumped.split("\n", -1)));
    }

    /// Index of the line starting top-level `key`, or -1. Only column-0 keys
    /// match, so a nested `name:` inside the `tags:` block is never mistaken
    /// for a top-level one.
    private static int indexOfKey(List<String> lines, String key) {
        Pattern keyLine = Pattern.compile("^" + Pattern.quote(key) + "\\s*:.*");
        for (int i = 0; i < lines.size(); i++) {
            if (keyLine.matcher(lines.get(i)).matches()) {
                return i;
            }
        }
        return -1;
    }

    /// The exclusive end of the block that opens at `start`: the first later
    /// line that starts a new top-level key or a column-0 comment (list items
    /// `- …`, indented lines, and blanks belong to the block), else the end.
    private static int blockEnd(List<String> lines, int start) {
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (TOP_LEVEL_KEY.matcher(line).matches() || TOP_LEVEL_COMMENT.matcher(line).matches()) {
                return i;
            }
        }
        // The last key's block ends before the document's trailing blank lines:
        // splitting a text that ends with a newline yields a final empty element,
        // and swallowing it would glue what follows the document — a frontmatter
        // block's closing `---` — onto the replaced key's last line.
        int end = lines.size();
        while (end > start + 1 && lines.get(end - 1).isEmpty()) {
            end--;
        }
        return end;
    }

    private static String appended(String doc, List<String> rendered) {
        String block = String.join("\n", rendered);
        if (doc.isEmpty()) {
            return block + "\n";
        }
        String sep = doc.endsWith("\n") ? "" : "\n";
        return doc + sep + block + "\n";
    }
}
