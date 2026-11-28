package com.contextswitcher.discovery;

import java.time.Instant;

import com.contextswitcher.discovery.ClaudeConversation.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~conversation-pane~1]
class ClaudeConversationTest {

    @Test
    void commandKeepsUserAndAssistantLinesWithoutTheBulk() {
        assertThat(String.join(" ", ClaudeConversation.command("/home/o/repo", "abc")))
                .isEqualTo("grep -a -e 'role.:.assistant' -e 'role.:.user' ~/.claude/projects/-home-o-repo/abc.jsonl"
                        + " | grep -a -v -e '{.tool_use_id.:' -e '{.type.:.tool_' -e '{.type.:.thinking.'"
                        + " -e '.type.:.api-request' | tail -n 80");
    }

    @Test
    void parseTellsUserFromClaudeAndSkipsEverythingElse() {
        String jsonl = """
                {"type":"user","timestamp":"2026-11-28T11:50:00Z","message":{"role":"user","content":"Sort the list"}}
                {"type":"user","isMeta":true,"message":{"role":"user","content":"Caveat: local commands"}}
                {"type":"user","message":{"role":"user","content":"<command-name>/model</command-name>"}}
                {"type":"user","message":{"role":"user","content":[{"tool_use_id":"toolu_1","type":"tool_result","content":"x"}]}}
                {"type":"assistant","timestamp":"2026-11-28T11:51:00Z","message":{"id":"m1","role":"assistant","content":[{"type":"text","text":"Done."}]}}
                {"type":"assistant","message":{"id":"m1","role":"assistant","content":[{"type":"tool_use"}]}}
                {"type":"assistant","message":{"id":"m1","role":"assistant","content":[{"type":"text","text":"**P1** first."}]}}
                {"type":"assistant","isSidechain":true,"message":{"id":"s","role":"assistant","content":[{"type":"text","text":"sub"}]}}
                {"type":"api-request-blob","message":{"role":"user","content":[{"type":"text","text":"reminder"}]}}
                {"type":"user","timestamp":"2026-11-28T11:52:00Z","message":{"role":"user","content":[{"type":"text","text":"And this?"},{"type":"image"}]}}
                {"type":"assistant","message":{"id":"m2","role":"assist
                """;
        assertThat(ClaudeConversation.parse(jsonl)).containsExactly(
                new Message(true, "Sort the list", Instant.parse("2026-11-28T11:50:00Z")),
                new Message(false, "Done.\n\n**P1** first.", Instant.parse("2026-11-28T11:51:00Z")),
                new Message(true, "And this?", Instant.parse("2026-11-28T11:52:00Z")));
    }
}
