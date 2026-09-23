package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.contextswitcher.queue.ClaudeMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.contextswitcher.queue.MessageSender;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.TaskFileParser;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Creates a fresh tmux window on a remote and starts a Claude session in
/// it with an initial prompt — the "task from PR" and "add remote Claude"
/// flows. Its window commands are shared with the resurrect (`TmuxResurrect`);
/// the prompt travels **as** a queued message ([MessageSender], once
/// Claude's TUI is up): over ssh **stdin** into a tmux buffer, pasted
/// bracketed — so it may span lines and carry any quotes (nothing appears
/// in an argv, where Windows ssh.exe would mangle double quotes) — with
/// `[image: …]`/`[file: …]` attachment markers uploaded and rewritten to
/// their remote paths like in any queued message.
// [impl->dsn~task-from-pr~6]
public class ClaudeWindowLauncher {

    private final SshCommandRunner ssh;
    private final MessageSender sender;
    private final boolean claudeAuto;

    /// `claudeAuto` starts Claude with `--dangerously-skip-permissions`
    /// (settings `claudeAuto`), so the session bootstraps unattended.
    public ClaudeWindowLauncher(SshCommandRunner ssh, MessageSender sender, boolean claudeAuto) {
        this.ssh = ssh;
        this.sender = sender;
        this.claudeAuto = claudeAuto;
    }

    /// Starts `claude` and waits for its TUI (`run-shell sleep` runs
    /// server-side after the chained tmux command, so the ssh call returns
    /// once Claude is up and the prompt paste can follow). With `auto`, the
    /// session skips permission prompts and runs the bootstrap unattended.
    // [impl->dsn~claude-auto-permissions~1]
    static List<String> startClaudeCommand(String windowId, boolean auto) {
        return startClaudeCommand(windowId, auto, null);
    }

    /// As [#startClaudeCommand(String, boolean)], but with `forkFrom` non-null
    /// starts `claude --resume <forkFrom> --fork-session` instead of a fresh
    /// `claude` — the source session's conversation, forked rather than
    /// continued, so the original session is left untouched
    /// (`dsn~task-fork~1`). Session ids are uuid-like (no quotes, spaces or
    /// shell metacharacters), so no extra quoting is needed around them.
    // [impl->dsn~task-fork~1]
    static List<String> startClaudeCommand(String windowId, boolean auto, @Nullable String forkFrom) {
        String target = "'" + windowId + "'";
        String base = forkFrom == null ? "claude" : "claude --resume " + forkFrom.strip() + " --fork-session";
        String claude = "'" + base + (auto ? " --dangerously-skip-permissions" : "") + "'";
        return List.of("tmux",
                "send-keys", "-t", target, claude, "Enter", "\\;",
                "run-shell", "'sleep 5'");
    }

    /// Creates the window (new session when the base session is gone) and
    /// launches Claude with the prompt. Returns the new immutable window
    /// id, or null with the failure logged.
    public @Nullable String launch(String remote, String session, String title, String prompt) {
        return launch(remote, session, title, prompt, null, ClaudeMode.DEFAULT);
    }

    /// As [#launch(String, String, String, String)], but leaving the fresh
    /// session's model and effort as they are.
    public @Nullable String launch(String remote, String session, String title, String prompt,
            @Nullable String cwd) {
        return launch(remote, session, title, prompt, cwd, ClaudeMode.DEFAULT);
    }

    /// As [#launch(String, String, String, String)], but starts the window
    /// (and Claude) in `cwd` when non-null — a group's per-task workspace —
    /// and switches the fresh session to `mode`'s model and effort before
    /// the prompt (the picks from the Add-task dialog).
    // [impl->dsn~claude-mode-select~3]
    public @Nullable String launch(String remote, String session, String title, String prompt,
            @Nullable String cwd, ClaudeMode mode) {
        return launch(remote, session, title, prompt, cwd, mode, step -> { }, windowId -> { });
    }

    /// As [#launch(String, String, String, String, String, ClaudeMode)], but
    /// reporting each remote round-trip it starts to `onStep` — the caller
    /// turns those into the creating task's row progress
    /// (`dsn~task-create-progress~5`) — and handing the fresh window's id to
    /// `onWindow` the moment it exists, well before Claude is up: the caller
    /// records it in the task file right then, so the terminal mirror can
    /// attach and the session can be watched starting. Both are called off the
    /// FX thread, so the consumers have to hop threads themselves.
    // [impl->dsn~task-create-progress~5]
    public @Nullable String launch(String remote, String session, String title, String prompt,
            @Nullable String cwd, ClaudeMode mode, Consumer<String> onStep,
            Consumer<String> onWindow) {
        return launch(remote, session, title, prompt, cwd, mode, onStep, onWindow, null);
    }

    /// As [#launch(String, String, String, String, String, ClaudeMode,
    /// Consumer, Consumer)], but with `forkFrom` non-null starts Claude
    /// resumed from that session id and forked, rather than fresh
    /// (`dsn~task-fork~1`) — the task-fork flow, whose prompt tells the fresh
    /// session it continues the source conversation.
    // [impl->dsn~task-fork~1]
    public @Nullable String launch(String remote, String session, String title, String prompt,
            @Nullable String cwd, ClaudeMode mode, Consumer<String> onStep,
            Consumer<String> onWindow, @Nullable String forkFrom) {
        onStep.accept("Creating the tmux window …");
        SshCommandRunner.SshResult created =
                ssh.run(remote, newWindowCommand(session, cwd, title));
        if (!created.ok()) {
            created = ssh.run(remote, newSessionCommand(session, cwd, title));
        }
        if (!created.ok() || created.stdout().isBlank()) {
            Logger.warn("Cannot create a window for session {} on {}: {}",
                    session, remote, created.stderr().strip());
            return null;
        }
        String windowId = created.stdout().strip();
        onWindow.accept(windowId);
        onStep.accept("Starting Claude …");
        SshCommandRunner.SshResult started =
                ssh.run(remote, startClaudeCommand(windowId, claudeAuto, forkFrom));
        if (!started.ok()) {
            Logger.warn("Window {} created on {}, but starting Claude failed: {}",
                    windowId, remote, started.stderr().strip());
            return windowId;
        }
        acceptTrustDialog(remote, windowId);
        if (!mode.commands().isEmpty()) {
            SshCommandRunner.SshResult settings = ssh.run(remote, SETTINGS_COMMAND);
            SshCommandRunner.SshResult pane = ssh.run(remote, capturePaneCommand(windowId));
            mode = withoutConfigured(mode, settings.ok() ? settings.stdout() : "",
                    pane.ok() ? pane.stdout() : "");
        }
        // One step per round-trip, so the row says which one is slow.
        ClaudeMode model = new ClaudeMode(mode.model(), null);
        ClaudeMode effort = new ClaudeMode(null, mode.effort());
        String error = null;
        if (!model.commands().isEmpty()) {
            onStep.accept("Switching the model …");
            error = sender.switchMode(remote, windowId, model);
        }
        if (error == null && !effort.commands().isEmpty()) {
            onStep.accept("Switching the effort …");
            error = sender.switchMode(remote, windowId, effort);
        }
        if (error == null) {
            onStep.accept("Sending the prompt …");
            error = sender.send(remote, windowId, prompt);
        }
        for (int retry = 1; error == null && retry <= PROMPT_RETRIES && !visible(remote, windowId, prompt); retry++) {
            Logger.info("Prompt not visible in window {} on {} after {} s, pasting it again ({}/{})",
                    windowId, remote, VERIFY_DELAY_MS / 1000, retry, PROMPT_RETRIES);
            error = sender.send(remote, windowId, prompt);
        }
        if (error != null) {
            Logger.warn("Claude started in window {} on {}, but sending the prompt failed: {}",
                    windowId, remote, error);
        }
        return windowId;
    }

    /// Reads the remote's user settings, where `/model` and `/effort` save
    /// the default a fresh session starts with; ssh starts in the home.
    static final List<String> SETTINGS_COMMAND = List.of("cat", ".claude/settings.json");

    /// The effort Claude's start banner names ("Opus 5 with medium effort").
    private static final Pattern BANNER_EFFORT = Pattern.compile("with (\\S+) effort");

    /// `mode` without the halves a fresh session already starts with — the
    /// model per `settingsJson` (`model`), the effort per the start banner in
    /// `pane`: the effort a session gets also comes from `CLAUDE_EFFORT` and
    /// per-model settings, which only the banner has resolved. Each skipped
    /// half saves a paste round. Unreadable settings or no banner skip nothing.
    // [impl->dsn~claude-mode-select~3]
    static ClaudeMode withoutConfigured(ClaudeMode mode, String settingsJson, String pane) {
        String effort = mode.effort();
        Matcher banner = BANNER_EFFORT.matcher(pane);
        if (effort != null && banner.find() && effort.strip().equals(banner.group(1))) {
            effort = null;
        }
        JsonNode settings;
        try {
            settings = new ObjectMapper().readTree(settingsJson);
        } catch (Exception e) {
            return new ClaudeMode(mode.model(), effort);
        }
        if (settings == null || !settings.isObject()) {
            return new ClaudeMode(mode.model(), effort);
        }
        String model = mode.model();
        if (model != null && (model.strip().equals(settings.path("model").asText(null))
                || model.strip().equals("default") && !settings.has("model"))) {
            model = null;
        }
        return new ClaudeMode(model, effort);
    }

    /// A directory Claude has never run in — the fresh workspaces root of
    /// a category created from a URL — greets it with the folder trust
    /// dialog ("Yes, I trust this folder"), whose default answer is "No,
    /// exit": the first Enter of the mode commands would quit Claude and
    /// the prompt would land in the shell. When the pane shows the dialog,
    /// answer it (Down to the trust option, Enter) and give the TUI a
    /// moment to come up before the prompt is sent.
    // [impl->dsn~claude-trust-dialog~1]
    private void acceptTrustDialog(String remote, String windowId) {
        SshCommandRunner.SshResult captured = ssh.run(remote, capturePaneCommand(windowId));
        if (captured.ok() && captured.stdout().contains(TRUST_PROMPT)) {
            Logger.info("Answering Claude's folder trust dialog in window {} on {}", windowId, remote);
            ssh.run(remote, trustCommand(windowId));
        }
    }

    /// The trust option's label, as Claude Code prints it.
    static final String TRUST_PROMPT = "Yes, I trust this folder";

    /// Moves to the trust option and confirms; the server-side sleep covers
    /// the TUI start that follows, like [#startClaudeCommand].
    static List<String> trustCommand(String windowId) {
        return List.of("tmux",
                "send-keys", "-t", "'" + windowId + "'", "Down", "Enter", "\\;",
                "run-shell", "'sleep 3'");
    }

    /// How often a prompt missing from the pane is pasted again: a paste
    /// right after a slash command is sometimes dropped more than once.
    static final int PROMPT_RETRIES = 3;

    /// Grace period before the pane is checked for the prompt.
    static final long VERIFY_DELAY_MS = 3000;

    /// Whether the pasted prompt shows in the window — a paste that lands
    /// while Claude's TUI is still (re)starting, e.g. right after the model
    /// switch, is silently dropped, and the chat then sits empty. Checked
    /// after a short grace period; the probe is the prompt's first line
    /// (see [#probe]), looked up in the joined pane text plus scrollback.
    private boolean visible(String remote, String windowId, String prompt) {
        String probe = probe(prompt);
        if (probe.isEmpty()) {
            return true;
        }
        try {
            Thread.sleep(VERIFY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
        SshCommandRunner.SshResult captured = ssh.run(remote, capturePaneCommand(windowId));
        return !captured.ok() || captured.stdout().contains(probe);
    }

    /// The pane's visible text plus recent scrollback, wrapped lines joined
    /// (`-J`), so the probe survives tmux's own wrapping.
    static List<String> capturePaneCommand(String windowId) {
        return List.of("tmux", "capture-pane", "-p", "-J", "-S", "-200", "-t", "'" + windowId + "'");
    }

    /// Text that must show once the prompt is in the chat: the start of its
    /// first non-blank line without an attachment marker (markers are
    /// rewritten to remote paths on the way), capped short enough to fit
    /// Claude's own line wrapping. Empty when no such line exists.
    static String probe(String prompt) {
        return prompt.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.contains("[image: ") && !line.contains("[file: "))
                .findFirst()
                .map(line -> (line.length() > 30 ? line.substring(0, 30) : line).strip())
                .orElse("");
    }

    /// `-P -F` prints the new window's immutable id; `-c` sets the start dir
    /// (omitted when no cwd is recorded); `-n` names the window after the task.
    public static List<String> newWindowCommand(String session, @Nullable String cwd, String name) {
        List<String> command = new ArrayList<>(List.of("tmux", "new-window", "-t", "'" + session + ":'"));
        if (cwd != null) {
            command.add("-c");
            command.add("'" + cwd + "'");
        }
        command.addAll(List.of("-n", SshCommandRunner.quote(name), "-P", "-F", "'#{window_id}'"));
        return command;
    }

    /// Fallback when even the session is gone (fresh tmux server after reboot).
    public static List<String> newSessionCommand(String session, @Nullable String cwd, String name) {
        List<String> command = new ArrayList<>(List.of("tmux", "new-session", "-d", "-s", "'" + session + "'"));
        if (cwd != null) {
            command.add("-c");
            command.add("'" + cwd + "'");
        }
        command.addAll(List.of("-n", SshCommandRunner.quote(name), "-P", "-F", "'#{window_id}'"));
        return command;
    }

    /// The context prompt pasted into a live task's Claude session. The
    /// user's input is the task **description**; it travels verbatim between
    /// `-----` fence lines (delivery over ssh stdin into a tmux buffer, so
    /// quotes and any other characters survive). The prompt asks Claude to
    /// derive a short task title and publish it as the `@cs_title` window
    /// option, which the title poller syncs back into the task file
    /// (`dsn~claude-title-sync~3`). The command targets the calling pane
    /// explicitly (`set -w -t "$TMUX_PANE"`): a bare `set -w` targets the
    /// session's *active* window, so if the user switches to another task
    /// while Claude derives the title, the option would land on that focused
    /// window and rename the wrong task. When the group uses a shared
    /// `workspacesRoot` (`bootstrapWorktree`), a clause tells Claude to
    /// create the task's git worktree from the group's primary clone — the
    /// `group` subdirectory of the workspace root — naming the GitHub `repo`
    /// when the group configures one (else the clone's origin already knows
    /// it, so nothing is asked). Deliberately terse so Claude starts on the
    /// task instead of a setup conversation.
    /// `appendix` — reference material a caller wants the session to have,
    /// the workspace-root `CLAUDE.md` template of a category created from a
    /// URL (`dsn~claude-md-templates~1`) — follows the closing fence, so the
    /// description the task file, its name and the tmux window carry stays
    /// short.
    // [impl->dsn~task-create-live~8]
    public static String liveTaskPrompt(String description, @Nullable String repo, boolean bootstrapWorktree,
            String group, @Nullable String appendix) {
        String appendixClause = appendix == null || appendix.isBlank()
                ? ""
                : "\n\n" + appendix.strip();
        return "Your task description is between the ----- lines below. " + TITLE_CLAUSE
                + worktreeClause(bootstrapWorktree, repo, group)
                + "\n-----\n" + description.strip() + "\n-----" + appendixClause;
    }

    /// The `@cs_title` clause both [#liveTaskPrompt] and the desktop's fork prompt open
    /// with: derive a short title and publish it via the pane-targeted
    /// `tmux set -w` (`dsn~claude-title-sync~3`) — explicit, since a bare
    /// `set -w` targets the session's *active* window, not necessarily the
    /// one the title belongs to.
    public static final String TITLE_CLAUSE = "First derive a short task title (3-6 words) and publish it"
            + " for ContextSwitcher by running: tmux set -w -t \"$TMUX_PANE\" @cs_title 'the title'.";

    /// The worktree-bootstrap clause both prompts append when the category
    /// uses a shared `workspacesRoot` — see [#liveTaskPrompt].
    public static String worktreeClause(boolean bootstrapWorktree, @Nullable String repo, String group) {
        if (!bootstrapWorktree) {
            return "";
        }
        String repoClause = repo == null || repo.isBlank()
                ? ""
                : " The GitHub repository is " + repo.strip() + ".";
        return " Create a git worktree for this task from the " + group.strip()
                + " subdirectory of the current directory and work in it." + repoClause;
    }

    /// A live task's file before its window exists: title (the description
    /// until Claude publishes a short one), `active`, `remote`, `claude.cwd`
    /// when the category gives a working directory, and the description under
    /// `# Notes`, so nothing is lost when the title is replaced. The launcher's
    /// window id is added afterwards (`TaskFileParser.withTmuxSection`).
    // [impl->dsn~task-create-live~8]
    public static String liveTaskContent(String description, String remote, @Nullable String workdir) {
        String claudeSection = workdir == null ? ""
                : "claude:\n  cwd: " + TaskFileParser.yamlScalar(workdir) + "\n";
        return """
                ---
                title: %s
                status: active
                remote: %s
                %s---

                # Notes

                %s
                """.formatted(TaskFileParser.yamlScalar(description), TaskFileParser.yamlScalar(remote),
                claudeSection, description.strip());
    }
}
