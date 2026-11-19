package com.contextswitcher.tasks;

import java.util.Locale;

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
    /// agent running. Vibe is a Python program and the Codex CLI a Node
    /// wrapper, so tmux names the interpreter for them.
    // ponytail: interpreter names also match any other Python/Node program in the pane; a per-agent `@cs_agent` option would be exact.
    public boolean runsIn(@Nullable String paneCommand) {
        if (paneCommand == null) {
            return false;
        }
        return switch (this) {
            case CLAUDE, CLAUDE_API -> paneCommand.equals("claude");
            case VIBE -> paneCommand.equals("vibe") || paneCommand.startsWith("python");
            case CODEX -> paneCommand.equals("codex") || paneCommand.equals("node");
        };
    }

    private String flags(boolean auto) {
        return auto ? " " + autoFlag : "";
    }
}
