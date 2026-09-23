package com.contextswitcher.tasks;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import com.contextswitcher.config.YamlPatch;

/// Reads and rewrites single frontmatter keys of a task or category file by
/// key path (`remote`, `intellij.projectPath`) — what the configuration form
/// ([com.contextswitcher.tasks.FrontmatterCatalog]) needs and the hand-written
/// mutators of [TaskFileParser] do not cover generically.
///
/// Writes go through [YamlPatch] on the frontmatter block alone, so only the
/// edited key's block is rewritten: comments, blank lines, key order, and keys
/// the app does not know about survive an edit that a YAML round-trip of the
/// whole document would flatten. The Markdown body after the closing fence is
/// never touched.
///
/// Machine-suffixed keys (`folders-windows`, `workspacesRoot-mylaptop`) are
/// deliberately *not* resolved here: the form edits the plain key, and the
/// suffixed variants stay where they are, editable in the raw YAML.
// [impl->dsn~task-field-form~4]
public final class Frontmatter {

    private static final String FENCE = "---";

    private Frontmatter() {
    }

    /// The YAML text between the file's `---` fences (its trailing newline
    /// kept), or null when the content carries no frontmatter at all.
    public static @Nullable String block(String content) {
        String normalized = normalize(content);
        if (!normalized.startsWith(FENCE + "\n")) {
            return null;
        }
        int close = normalized.indexOf("\n" + FENCE, FENCE.length());
        return close < 0 ? null : normalized.substring(FENCE.length() + 1, close + 1);
    }

    /// `content` with its frontmatter block replaced by `block` (which must end
    /// with a newline), the body left as it is. Content without a frontmatter
    /// fence is returned unchanged.
    public static String withBlock(String content, String block) {
        String normalized = normalize(content);
        int close = normalized.indexOf("\n" + FENCE, FENCE.length());
        if (!normalized.startsWith(FENCE + "\n") || close < 0) {
            return content;
        }
        return FENCE + "\n" + block + normalized.substring(close + 1);
    }

    /// The frontmatter as a map. An empty map when the file has none or its
    /// YAML does not parse — the form's caller then offers the raw view for
    /// repair rather than failing the whole dialog.
    public static Map<String, Object> parse(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        String block = block(content);
        if (block == null) {
            return result;
        }
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(block);
        } catch (YAMLException e) {
            return result;
        }
        if (loaded instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }

    /// The value at `path` — a top-level `key` or one nested `parent.child` —
    /// or null when it is not set (a `parent` that is not a mapping included).
    public static @Nullable Object get(Map<String, Object> data, String path) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            return data.get(path);
        }
        return data.get(path.substring(0, dot)) instanceof Map<?, ?> parent
                ? parent.get(path.substring(dot + 1))
                : null;
    }

    /// `content` with top-level `key` set to `value`, or — for a null `value` —
    /// with the key removed. Content without a frontmatter fence is returned
    /// unchanged, so a half-written file is never rewritten into a new shape.
    public static String set(String content, String key, @Nullable Object value) {
        String block = block(content);
        if (block == null) {
            return content;
        }
        return withBlock(content,
                value == null ? YamlPatch.remove(block, key) : YamlPatch.set(block, key, value));
    }

    /// The parser's own normalization — `\r\n` → `\n`, BOM dropped — so a file
    /// written by another editor is cut at the same fences.
    private static String normalize(String content) {
        String normalized = content.replace("\r\n", "\n");
        return normalized.startsWith("﻿") ? normalized.substring(1) : normalized;
    }
}
