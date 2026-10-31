package com.contextswitcher.tasks.title;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// The rules shared by every [TaskTitleSummarizer]: when a text is a
/// description rather than a title, and what a usable title looks like.
// [impl->dsn~task-create-local-title~2]
public final class TaskTitles {

    /// The longest title kept — about what a task row shows before it
    /// ellipsizes in a half-width list pane.
    public static final int MAX_LENGTH = 60;

    /// More words than this read as a sentence, not as a title.
    public static final int MAX_WORDS = 8;

    /// Wrapping a model's answer may carry despite being told not to.
    private static final Pattern ENCLOSING = Pattern.compile("^[\"'`*_]+|[\"'`*_]+$");

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s.,;:!?-]+$");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /// A markup tag such as `<invoke_tool>`: what a model emits when it tries
    /// to act on a description instead of naming it.
    private static final Pattern TAG = Pattern.compile("</?[A-Za-z_][\\w:-]*[^<>]*>");

    private TaskTitles() {
    }

    /// Whether `text` is a description worth summarizing: more than one line,
    /// longer than [#MAX_LENGTH], or more than [#MAX_WORDS] words. Anything
    /// shorter is taken as the title the user meant to type.
    public static boolean looksLikeDescription(String text) {
        String stripped = Objects.requireNonNull(text, "text").strip();
        return stripped.lines().count() > 1
                || stripped.length() > MAX_LENGTH
                || wordCount(stripped) > MAX_WORDS;
    }

    /// The first non-blank line of `raw`, with enclosing quotes or Markdown
    /// emphasis, trailing punctuation and runs of whitespace removed, cut at a
    /// word boundary to [#MAX_LENGTH]; empty when nothing is left.
    public static Optional<String> sanitize(String raw) {
        String line = Objects.requireNonNull(raw, "raw").lines()
                .map(String::strip)
                .filter(candidate -> !candidate.isEmpty())
                .findFirst()
                .orElse("");
        String cleaned = withoutTrailingPunctuation(unwrapped(WHITESPACE.matcher(line).replaceAll(" ")));
        cleaned = withoutTrailingPunctuation(truncate(cleaned));
        return cleaned.isEmpty() ? Optional.empty() : Optional.of(cleaned);
    }

    /// A model's answer made into a title: like [#sanitize], but empty when the
    /// first non-blank line is no title at all — it carries a markup tag (the
    /// model tried to call a tool or answered in markup) or has more than
    /// [#MAX_WORDS] words (it answered with a sentence, e.g. replied to the
    /// description instead of naming it). The caller then falls back to a
    /// title of its own rather than showing that answer as the task's name.
    public static Optional<String> fromModelAnswer(String raw) {
        String line = Objects.requireNonNull(raw, "raw").lines()
                .map(String::strip)
                .filter(candidate -> !candidate.isEmpty())
                .findFirst()
                .orElse("");
        if (TAG.matcher(line).find() || wordCount(line) > MAX_WORDS) {
            return Optional.empty();
        }
        return sanitize(line);
    }

    /// Punctuation first, then the wrapping: `"Fix login".` is quoted too.
    private static String unwrapped(String text) {
        return ENCLOSING.matcher(withoutTrailingPunctuation(text)).replaceAll("").strip();
    }

    private static String withoutTrailingPunctuation(String text) {
        return TRAILING_PUNCTUATION.matcher(text).replaceAll("");
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_LENGTH) {
            return text;
        }
        int lastSpace = text.lastIndexOf(' ', MAX_LENGTH);
        return text.substring(0, lastSpace > 0 ? lastSpace : MAX_LENGTH);
    }

    private static long wordCount(String text) {
        return text.isEmpty() ? 0 : WHITESPACE.split(text).length;
    }
}
