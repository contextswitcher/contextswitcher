package com.contextswitcher.tasks;

import java.util.List;
import java.util.Locale;

import com.contextswitcher.queue.ClaudeMode;

import org.jspecify.annotations.Nullable;

/// The coding-agent CLI a task's tmux window runs — chosen per category
/// (`agent:` in `CONTEXTSWITCHER.md`) and per task (`claude.agent:`), Claude
/// Code when neither says. Hard-coded on purpose: each CLI has its own
/// resume and permission flags, and no config can describe what the app
/// knows only for these.
///
/// [#CLAUDE_API] is Claude Code billed to an API key instead of the
/// subscription (another limit pool): the key is taken from
/// `$CS_ANTHROPIC_API_KEY` in the window's shell — set by direnv, `.bashrc` or
/// anything else — so a plain [#CLAUDE] in the same folder keeps the
/// subscription. An unset variable stops the command with the shell's
/// `parameter null or not set` instead of silently falling back.
// [impl->dsn~task-agent~1]
public enum Agent {
    CLAUDE("claude", "claude", "--dangerously-skip-permissions",
            "claude --resume %s", "claude --continue", "claude --resume %s --fork-session"),
    CLAUDE_API("claude-api", "ANTHROPIC_API_KEY=${CS_ANTHROPIC_API_KEY:?} claude",
            "--dangerously-skip-permissions",
            "ANTHROPIC_API_KEY=${CS_ANTHROPIC_API_KEY:?} claude --resume %s",
            "ANTHROPIC_API_KEY=${CS_ANTHROPIC_API_KEY:?} claude --continue",
            "ANTHROPIC_API_KEY=${CS_ANTHROPIC_API_KEY:?} claude --resume %s --fork-session"),
    // `--trust`: the folder trust question would block the prompt paste, as
    // Claude's does (the launcher answers that one).
    VIBE("vibe", "vibe --trust", "--auto-approve",
            "vibe --trust --resume %s", "vibe --trust --continue", null),
    CODEX("codex", "codex", "--dangerously-bypass-approvals-and-sandbox",
            "codex resume %s", "codex resume --last", "codex fork %s");

    private final String id;
    private final String start;
    private final String autoFlag;
    private final String resume;
    private final String continueLast;
    private final @Nullable String fork;

    Agent(String id, String start, String autoFlag, String resume, String continueLast,
            @Nullable String fork) {
        this.id = id;
        this.start = start;
        this.autoFlag = autoFlag;
        this.resume = resume;
        this.continueLast = continueLast;
        this.fork = fork;
    }

    /// The value written in the frontmatter (`claude-api`, `vibe`, …).
    public String id() {
        return id;
    }

    /// The agent named `value` (case-insensitive), or null for a blank or
    /// unknown one.
    public static @Nullable Agent parse(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String wanted = value.strip().toLowerCase(Locale.ROOT);
        for (Agent agent : values()) {
            if (agent.id.equals(wanted)) {
                return agent;
            }
        }
        return null;
    }

    /// Whether this is Claude Code (either billing): only it has the
    /// `/model`/`/effort` commands, the hooks and the pane patterns the app
    /// knows.
    public boolean isClaude() {
        return this == CLAUDE || this == CLAUDE_API;
    }

    /// The shell line starting a fresh session; with `auto`, one that asks
    /// no permission questions (settings `claudeAuto`).
    public String startLine(boolean auto) {
        return start + flags(auto);
    }

    /// The model picker's entries for this CLI. Claude Code's aliases; Vibe's
    /// model ids from its catalog (3.1.3); Codex's are guessed.
    // [impl->dsn~agent-mode-select~1]
    public List<String> models() {
        return switch (this) {
            case CLAUDE, CLAUDE_API -> ClaudeMode.MODELS;
            case VIBE -> List.of("mistral-large-4-0", "mistral-medium-latest", "zai-glm-5-3",
                    "zai-glm-5-3-flash");
            // ponytail: guessed, no Codex at hand; read `codex debug models` (or similar) once one is installed.
            case CODEX -> List.of("gpt-5.5", "gpt-5.5-codex", "gpt-5.5-mini");
        };
    }

    /// The effort picker's entries; empty for Vibe, whose thinking level
    /// cannot be set from outside an interactive session.
    // [impl->dsn~agent-mode-select~1]
    public List<String> efforts() {
        return switch (this) {
            case CLAUDE, CLAUDE_API -> ClaudeMode.EFFORTS;
            case VIBE -> List.of();
            case CODEX -> List.of("minimal", "low", "medium", "high", "xhigh");
        };
    }

    /// [#startLine(boolean)] with `mode`'s model and effort applied at the
    /// start — for the CLIs that cannot be switched by a typed command
    /// (Vibe's and Codex's `/model` only open a picker): `VIBE_ACTIVE_MODEL`
    /// for Vibe, `-m`/`-c model_reasoning_effort=` for Codex. A value not in
    /// [#models()]/[#efforts()] is dropped, so nothing unchecked reaches the
    /// shell. Claude Code gets its picks as `/model`/`/effort` after the
    /// start instead, so its line is unchanged.
    // [impl->dsn~agent-mode-select~1]
    public String startLine(boolean auto, ClaudeMode mode) {
        String model = mode.model() != null && models().contains(mode.model()) ? mode.model() : null;
        String effort = mode.effort() != null && efforts().contains(mode.effort()) ? mode.effort() : null;
        return switch (this) {
            case CLAUDE, CLAUDE_API -> startLine(auto);
            case VIBE -> (model == null ? "" : "VIBE_ACTIVE_MODEL=" + model + " ") + startLine(auto);
            case CODEX -> startLine(auto) + (model == null ? "" : " -m " + model)
                    + (effort == null ? "" : " -c model_reasoning_effort=" + effort);
        };
    }

    /// The shell line resuming `sessionId`, falling back to a fresh session
    /// when the CLI no longer knows the id (or later exits non-zero) — so the
    /// window never ends at a bare prompt. Without an id: Claude starts
    /// fresh (its id is always recorded via the hooks), the others continue
    /// their last session, since nothing records their id yet.
    public String resumeLine(@Nullable String sessionId, boolean auto) {
        if (sessionId == null) {
            return isClaude() ? startLine(auto) : continueLast + flags(auto) + " || " + startLine(auto);
        }
        return resume.formatted(sessionId.strip()) + flags(auto) + " || " + startLine(auto);
    }

    /// The shell line forking `sessionId` into a new session, or a fresh
    /// start for a CLI that cannot fork (Vibe).
    public String forkLine(String sessionId, boolean auto) {
        return fork == null ? startLine(auto) : fork.formatted(sessionId.strip()) + flags(auto);
    }

    /// Whether `paneCommand` (tmux's `#{pane_current_command}`) is this
    /// agent running. Vibe shows as `vibe-rs` (seen with 3.1.3); the Codex
    /// CLI's npm package is a Node wrapper, so tmux may name `node` for it.
    // ponytail: `node` also matches any other Node program in the pane; a per-agent `@cs_agent` option would be exact.
    public boolean runsIn(@Nullable String paneCommand) {
        if (paneCommand == null) {
            return false;
        }
        return switch (this) {
            case CLAUDE, CLAUDE_API -> paneCommand.equals("claude");
            case VIBE -> paneCommand.startsWith("vibe");
            case CODEX -> paneCommand.equals("codex") || paneCommand.equals("node");
        };
    }

    private String flags(boolean auto) {
        return auto ? " " + autoFlag : "";
    }
}
