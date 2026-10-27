package com.contextswitcher.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~android-voice-tour~1]
class ClaudeTranscriptTest {

    private static final String TEXT_LINE = """
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"thinking","thinking":"hmm"},{"type":"text","text":" The build is green. "},{"type":"text","text":"Next: push."}]}}""";
    private static final String TOOL_LINE = """
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","name":"Bash","input":{"command":"echo '\\"type\\":\\"text\\"'"}}]}}""";

    @Test
    void joinsTheTextBlocksOfTheNewestLineThatHasAny() {
        assertThat(ClaudeTranscript.lastAnswer(TEXT_LINE + "\n" + TOOL_LINE + "\n"))
                .isEqualTo("The build is green.\n\nNext: push.");
    }

    @Test
    void toolUseOnlyLinesAndGarbageYieldNothing() {
        assertThat(ClaudeTranscript.lastAnswer(TOOL_LINE)).isNull();
        assertThat(ClaudeTranscript.lastAnswer("not json\n")).isNull();
        assertThat(ClaudeTranscript.lastAnswer("")).isNull();
    }

    @Test
    void remoteCommandReadsOnlyTheAssistantTail() {
        assertThat(String.join(" ", ClaudeTranscript.remoteCommand("/home/o/jabref", "abc-123")))
                .isEqualTo("grep -F '\"type\":\"assistant\"' ~/.claude/projects/-home-o-jabref/abc-123.jsonl | tail -5");
    }
}
