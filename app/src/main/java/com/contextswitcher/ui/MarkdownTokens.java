package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Line-based tokenizer for task files: YAML configuration (keys, comments)
/// and Markdown notes (headings, bullets, quotes, inline code) — one method
/// per tab of the editor lane. Pure logic — the decorator maps the segments
/// onto rich-text styles.
// [impl->dsn~markdown-syntax-highlighting~3]
public final class MarkdownTokens {

    public enum Style { PLAIN, YAML_KEY, COMMENT, HEADING, BULLET, QUOTE, CODE }

    public record Seg(String text, Style style) {
    }

    private static final Pattern YAML_KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_-]+:)(.*)$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*[-*+] )(.*)$");

    private MarkdownTokens() {
    }

    public static List<Seg> yamlLine(String line) {
        String stripped = line.stripLeading();
        if (stripped.startsWith("#")) {
            return List.of(new Seg(line, Style.COMMENT));
        }
        Matcher key = YAML_KEY.matcher(line);
        if (key.matches()) {
            List<Seg> segs = new ArrayList<>();
            if (!key.group(1).isEmpty()) {
                segs.add(new Seg(key.group(1), Style.PLAIN));
            }
            segs.add(new Seg(key.group(2), Style.YAML_KEY));
            appendYamlValue(segs, key.group(3));
            return segs;
        }
        return List.of(new Seg(line, Style.PLAIN));
    }

    /// Value part after `key:`; a trailing ` # comment` is styled as comment.
    private static void appendYamlValue(List<Seg> segs, String value) {
        int hash = value.indexOf(" #");
        if (hash < 0) {
            if (!value.isEmpty()) {
                segs.add(new Seg(value, Style.PLAIN));
            }
            return;
        }
        if (hash > 0) {
            segs.add(new Seg(value.substring(0, hash), Style.PLAIN));
        }
        segs.add(new Seg(value.substring(hash), Style.COMMENT));
    }

    public static List<Seg> markdownLine(String line) {
        if (line.startsWith("#")) {
            return List.of(new Seg(line, Style.HEADING));
        }
        if (line.stripLeading().startsWith(">")) {
            return List.of(new Seg(line, Style.QUOTE));
        }
        Matcher bullet = BULLET.matcher(line);
        if (bullet.matches()) {
            List<Seg> segs = new ArrayList<>();
            segs.add(new Seg(bullet.group(1), Style.BULLET));
            segs.addAll(inlineCode(bullet.group(2)));
            return segs;
        }
        return inlineCode(line);
    }

    /// Splits on backticks: odd chunks are inline code (backticks kept visible).
    private static List<Seg> inlineCode(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.indexOf('`') < 0) {
            return List.of(new Seg(text, Style.PLAIN));
        }
        List<Seg> segs = new ArrayList<>();
        String[] chunks = text.split("`", -1);
        for (int i = 0; i < chunks.length; i++) {
            if (i % 2 == 1 && i + 1 < chunks.length) {
                segs.add(new Seg("`" + chunks[i] + "`", Style.CODE));
            } else if (i % 2 == 1) {
                segs.add(new Seg("`" + chunks[i], Style.PLAIN));
            } else if (!chunks[i].isEmpty()) {
                segs.add(new Seg(chunks[i], Style.PLAIN));
            }
        }
        return segs;
    }
}
