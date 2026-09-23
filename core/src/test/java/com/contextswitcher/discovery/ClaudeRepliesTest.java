package com.contextswitcher.discovery;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-markdown-copy~1]
class ClaudeRepliesTest {

    private static final String REPLY = """
            ## Summary

            The fix lives in **`TaskFileParser`** now, and the test pins it.

            - first point of the list
            - second point, with `code`
            """;

    @Test
    void commandGrepsAssistantTextLinesOfTheTranscript() {
        assertThat(String.join(" ", ClaudeReplies.command("/home/o/repo", "abc")))
                .isEqualTo("grep -a 'role.:.assistant' ~/.claude/projects/-home-o-repo/abc.jsonl"
                        + " | grep -a 'type.:.text' | tail -n 40");
    }

    @Test
    void parseJoinsTextBlocksOfOneMessageAndSkipsOthers() {
        String jsonl = """
                {"message":{"id":"m1","role":"assistant","content":[{"type":"text","text":"old"}]}}
                {"message":{"id":"m2","role":"assistant","content":[{"type":"text","text":"a"}]}}
                {"message":{"id":"m2","role":"assistant","content":[{"type":"text","text":"b"}]}}
                {"isSidechain":true,"message":{"id":"s","role":"assistant","content":[{"type":"text","text":"sub"}]}}
                {"message":{"id":"m3","role":"assistant","content":[{"type":"tool_use"}]}}
                {"message":{"id":"m4","role":"assist
                """;
        assertThat(ClaudeReplies.parse(jsonl)).containsExactly("old", "a\n\nb");
    }

    @Test
    void renderedParagraphGetsItsMarkdownBack() {
        String rendered = "⏺ The fix lives in TaskFileParser now, and\n  the test pins it.";
        assertThat(ClaudeReplies.markdownFor(rendered, List.of(REPLY)))
                .isEqualTo("The fix lives in **`TaskFileParser`** now, and the test pins it.");
    }

    @Test
    void wholeLinesKeepTheirHeadingAndBullets() {
        String rendered = "Summary\n\nThe fix lives in TaskFileParser now, and the test pins it.\n\n"
                + "  • first point of the list\n  • second point, with code";
        assertThat(ClaudeReplies.markdownFor(rendered, List.of("other reply text here", REPLY)))
                .isEqualTo(REPLY.strip());
    }

    @Test
    void midLineSelectionWidensOverHuggingSyntaxOnly() {
        assertThat(ClaudeReplies.markdownFor("lives in TaskFileParser now", List.of(REPLY)))
                .isEqualTo("lives in **`TaskFileParser`** now");
    }

    @Test
    void shortOrUnknownSelectionIsNull() {
        assertThat(ClaudeReplies.markdownFor("TaskFile", List.of(REPLY))).isNull();
        assertThat(ClaudeReplies.markdownFor("nothing of this was ever written", List.of(REPLY))).isNull();
    }
}
