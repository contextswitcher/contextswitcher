package com.contextswitcher.tasks.title;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// A title without any model: the description's first sentence, limited to
/// [TaskTitles#MAX_WORDS] words. Always available, so it is the fallback
/// when the model-backed summarizer cannot answer.
// [impl->dsn~task-create-local-title~1]
public final class FirstWordsSummarizer implements TaskTitleSummarizer {

    /// A sentence ends at `.`, `!` or `?` followed by whitespace or the end,
    /// or at a line break — so the dots inside a URL or `1.5` do not.
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](?=\\s|$)|\\R");

    @Override
    public Optional<String> summarize(String description) {
        String text = Objects.requireNonNull(description, "description").strip();
        Matcher end = SENTENCE_END.matcher(text);
        String sentence = end.find() ? text.substring(0, end.start()) : text;
        String words = Arrays.stream(sentence.strip().split("\\s+"))
                .limit(TaskTitles.MAX_WORDS)
                .collect(Collectors.joining(" "));
        return TaskTitles.sanitize(words);
    }
}
