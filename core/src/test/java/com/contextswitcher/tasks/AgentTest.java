package com.contextswitcher.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contextswitcher.queue.ClaudeMode;
import com.contextswitcher.switching.ClaudeWindowLauncher;
import org.junit.jupiter.api.Test;

// [utest->dsn~task-agent~1]
class AgentTest {

    private static final String TASK = """
            ---
            title: t
            status: active
            remote: devbox
            claude:
              cwd: /w
            %s---
            """;

    private final TaskFileParser parser = new TaskFileParser();

    private Task task(String extraClaude) throws Exception {
        return parser.parse("g/t", TASK.formatted(extraClaude));
    }

    @Test
    void taskAgentWinsOverCategoryAndDefaultIsClaude() throws Exception {
        GroupConfig vibeCategory = parser.parseGroupConfig("---\nagent: vibe\n---\n");
        assertEquals(Agent.VIBE, vibeCategory.agent());
        assertEquals(Agent.VIBE, task("").agent(vibeCategory));
        assertEquals(Agent.CODEX, task("  agent: codex\n").agent(vibeCategory));
        assertEquals(Agent.CLAUDE, task("").agent(GroupConfig.EMPTY));
        assertEquals(Agent.VIBE, task("").withCategoryDefaults(vibeCategory).claude().agentOrDefault());
    }

    @Test
    void unknownAgentCountsAsUnset() throws Exception {
        assertNull(parser.parseGroupConfig("---\nagent: gpt-9\n---\n").agent());
        assertEquals(Agent.CLAUDE_API, Agent.parse(" Claude-API "));
    }

    @Test
    void claudeLinesKeepTodaysShape() {
        assertEquals("claude --dangerously-skip-permissions", Agent.CLAUDE.startLine(true));
        assertEquals("claude --resume abc || claude", Agent.CLAUDE.resumeLine("abc", false));
        assertEquals("claude", Agent.CLAUDE.resumeLine(null, false));
        assertEquals("claude --resume abc --fork-session", Agent.CLAUDE.forkLine("abc", false));
    }

    @Test
    void apiKeyComesFromTheShellAndMustBeSet() {
        assertEquals("ANTHROPIC_API_KEY=${CS_ANTHROPIC_API_KEY:?} claude --resume abc"
                + " || ANTHROPIC_API_KEY=${CS_ANTHROPIC_API_KEY:?} claude", Agent.CLAUDE_API.resumeLine("abc", false));
    }

    @Test
    void otherAgentsContinueWithoutAnIdAndUseTheirOwnFlags() {
        assertEquals("vibe --trust --continue --auto-approve || vibe --trust --auto-approve",
                Agent.VIBE.resumeLine(null, true));
        assertEquals("vibe --trust", Agent.VIBE.forkLine("abc", false));
        assertEquals("codex resume --last || codex", Agent.CODEX.resumeLine(null, false));
        assertEquals("codex fork abc --dangerously-bypass-approvals-and-sandbox", Agent.CODEX.forkLine("abc", true));
    }

    @Test
    void paneCommandMatchesTheInterpreter() {
        assertTrue(Agent.CLAUDE_API.runsIn("claude"));
        assertTrue(Agent.VIBE.runsIn("vibe-rs"));
        assertTrue(Agent.CODEX.runsIn("node"));
        assertFalse(Agent.CLAUDE.runsIn("bash"));
        assertFalse(Agent.VIBE.runsIn(null));
    }

    /// Vibe and Codex take the pick on the start line; an unlisted value
    /// (a Claude alias, a typo) never reaches the shell.
    // [utest->dsn~agent-mode-select~1]
    @Test
    void modelAndEffortGoOnTheStartLine() {
        assertEquals("VIBE_ACTIVE_MODEL=mistral-medium-latest vibe --trust",
                Agent.VIBE.startLine(false, new ClaudeMode("mistral-medium-latest", "high")));
        assertEquals("vibe --trust", Agent.VIBE.startLine(false, new ClaudeMode("opus", null)));
        assertEquals("codex -m gpt-5.5 -c model_reasoning_effort=high",
                Agent.CODEX.startLine(false, new ClaudeMode("gpt-5.5", "high")));
        assertEquals("claude", Agent.CLAUDE.startLine(false, new ClaudeMode("opus", "max")));
        assertTrue(Agent.VIBE.efforts().isEmpty());
    }

    /// The Add-task pick lands in the new file, so a resume keeps the CLI.
    // [utest->dsn~agent-mode-select~1]
    @Test
    void livetaskRecordsThePickedAgent() throws Exception {
        String content = ClaudeWindowLauncher.liveTaskContent("Do it", "devbox", "/w", null, Agent.CODEX);
        assertEquals(Agent.CODEX, parser.parse("g/t", content).agent(GroupConfig.EMPTY));
    }
}
