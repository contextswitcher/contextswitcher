package com.contextswitcher.ui;

import com.contextswitcher.ssh.SshCommandRunner;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.contextswitcher.analysis.RefactoringMinerCommands;

/// The tools the setup wizard recommends on a session host and installs in
/// one click each: RefactoringMiner behind the refactoring badge and the
/// AST-diff view, ast-grep and codegraph with their Claude Code skills —
/// what makes a session good rather than what the app itself needs, so
/// none of them gates the wizard.
///
/// Pure argv builders (`sh -c '<script>'`), run through an
/// `SshCommandRunner` on the remote or, via `HostCommandRunner`, on this
/// machine. Same transport constraint as every remote command: no double
/// quotes anywhere (`dsn~ssh-command-runner~7`).
// [impl->dsn~recommended-tools~3]
public final class RecommendedTools {

    /// One tool: its `label`, one line of `why`, `installs` — where *Install*
    /// puts what, for its tooltip —, the `probe` printing its version
    /// (non-zero exit when missing), the `install` script — printing the
    /// version last — and what the install reads on stdin (the codegraph
    /// skill file), or null.
    public record Tool(String key, String label, String why, String installs, List<String> probe,
            List<String> install, byte @Nullable [] installInput) {
    }

    /// The ast-grep release the wizard installs (the binary from the GitHub
    /// release zip, into `~/.local/bin`; the zip's `sg` is left out — it
    /// would shadow shadow-utils' `sg`).
    public static final String AST_GREP_VERSION = "0.45.3";
    /// The codegraph release the wizard installs, pinned for the installer.
    public static final String CODEGRAPH_VERSION = "v1.6.0";

    /// `~/.local/bin` is where the native installers put their binaries and
    /// where a non-interactive shell does not look.
    private static final String PATH_FIX = "PATH=$PATH:$HOME/.local/bin; ";

    private RecommendedTools() {
    }

    /// The tools for a remote host: the reporting hooks and all three tools.
    /// `refactoringMinerHome` is the configured directory to probe, null for
    /// the default one.
    public static List<Tool> remote(@Nullable String refactoringMinerHome) {
        return List.of(reportingHooks(), refactoringMiner(refactoringMinerHome), astGrep(), codegraph());
    }

    /// The tools for this machine: RefactoringMiner is left out, since the
    /// app runs it over ssh only (`dsn~refactoring-miner-commands~1`).
    public static List<Tool> local() {
        return List.of(reportingHooks(), astGrep(), codegraph());
    }

    /// The `@cs_*` options every hook publishes, as the probe greps for them
    /// (`.` for the space, which a quoted word would carry in double quotes).
    static final List<String> HOOK_MARKERS = List.of("cs_status.working", "cs_status.waiting",
            "cs_status.attention", "cs_session_id", "cs_workspace");

    /// The Claude Code hooks that make a session report itself
    /// (`dsn~claude-report-hooks~1`): status, session id and working
    /// directory as tmux window options. Without them the app never learns
    /// a session's id or worktree — no status dot, no transcript or worktree
    /// to remove on delete (field report 2026-10-12, a fresh host).
    static Tool reportingHooks() {
        StringBuilder probe = new StringBuilder("S=$HOME/.claude/settings.json; command -v jq >/dev/null");
        for (String marker : HOOK_MARKERS) {
            probe.append(" && grep -q ").append(marker).append(" $S");
        }
        probe.append(" && echo in ~/.claude/settings.json");
        return new Tool("claude-hooks", "Status reporting hooks",
                "Claude Code hooks that publish each session's status, id and working directory "
                        + "to its tmux window: the live status dots, resume, Show diff and the "
                        + "worktree cleanup on delete rely on them. Sessions started before the "
                        + "install pick them up on their next start.",
                "Adds the missing hooks to ~/.claude/settings.json, keeping every setting and hook "
                        + "already there (a copy of the file goes to settings.json.before-contextswitcher). "
                        + "Needs jq on the host; an unreadable settings.json is left alone.",
                script(probe.toString()), List.of("sh", "-s"),
                HOOKS_INSTALL.getBytes(StandardCharsets.UTF_8));
    }

    /// The hooks installer, fed to `sh -s` on stdin — a script of its own
    /// rather than an argv, since the hooks' JSON is full of the double
    /// quotes no argv may carry. `jq` merges per event: a hook is added when
    /// no command of that event publishes the same option already, so the
    /// README's hand-copied hooks and a second run add nothing. Every hook is
    /// guarded by `$TMUX_PANE` and targets it (README, *Let Claude report its
    /// state*); the working directory comes from the `cwd` of the hook input
    /// after each Bash call, where a `cd` into a new worktree shows.
    // [impl->dsn~claude-report-hooks~1]
    static final String HOOKS_INSTALL = """
            command -v jq >/dev/null || { echo "jq is missing on this host - install it, then press Install again" >&2; exit 1; }
            S="$HOME/.claude/settings.json"
            mkdir -p "$HOME/.claude" || exit 1
            [ -s "$S" ] || echo '{}' > "$S" || exit 1
            jq -e 'type == "object"' "$S" >/dev/null 2>&1 || { echo "$S is no JSON object - left untouched" >&2; exit 1; }
            CS=$(cat <<'EOF'
            {
              "UserPromptSubmit": [
                {"hooks": [{"type": "command", "command": "[ -n \\"$TMUX_PANE\\" ] && tmux set -w -t \\"$TMUX_PANE\\" @cs_status working 2>/dev/null || true"}]}
              ],
              "Stop": [
                {"hooks": [{"type": "command", "command": "[ -n \\"$TMUX_PANE\\" ] && tmux set -w -t \\"$TMUX_PANE\\" @cs_status waiting 2>/dev/null || true"}]}
              ],
              "Notification": [
                {"hooks": [{"type": "command", "command": "[ -n \\"$TMUX_PANE\\" ] && tmux set -w -t \\"$TMUX_PANE\\" @cs_status attention 2>/dev/null || true"}]}
              ],
              "SessionStart": [
                {"hooks": [{"type": "command", "command": "[ -n \\"$TMUX_PANE\\" ] && tmux set -w -t \\"$TMUX_PANE\\" @cs_session_id \\"$(jq -r .session_id)\\" 2>/dev/null || true"}]},
                {"matcher": "startup|resume|clear", "hooks": [{"type": "command", "command": "[ -n \\"$TMUX_PANE\\" ] && tmux set -w -t \\"$TMUX_PANE\\" @cs_status waiting 2>/dev/null || true"}]}
              ],
              "PostToolUse": [
                {"matcher": "Bash", "hooks": [{"type": "command", "command": "[ -n \\"$TMUX_PANE\\" ] && tmux set -w -t \\"$TMUX_PANE\\" @cs_workspace \\"$(jq -r .cwd)\\" 2>/dev/null || true"}]}
              ]
            }
            EOF
            )
            jq --argjson cs "$CS" '
              def marker: [capture("(?<m>@cs_[a-z_]+( (working|waiting|attention))?)") | .m][0] // "";
              def present($e; $m): [(.hooks[$e] // [])[] | (.hooks // [])[] | (.command // "") | select(contains($m))] | length > 0;
              reduce ($cs | to_entries[] | .key as $e | .value[] | {e: $e, g: .}) as $x (.;
                if present($x.e; ($x.g.hooks[0].command | marker)) then .
                else .hooks[$x.e] = ((.hooks[$x.e] // []) + [$x.g]) end)
            ' "$S" > "$S.contextswitcher-new" || { rm -f "$S.contextswitcher-new"; exit 1; }
            if cmp -s "$S" "$S.contextswitcher-new"; then
              rm -f "$S.contextswitcher-new"
            else
              cp "$S" "$S.before-contextswitcher" && mv "$S.contextswitcher-new" "$S" || exit 1
            fi
            echo in ~/.claude/settings.json
            """;

    /// Probes `home` when configured (an install under a custom path shows
    /// as found), else the wizard's own default directory; the installer
    /// always targets the default.
    static Tool refactoringMiner(@Nullable String home) {
        String probe = "H=" + (home == null ? RefactoringMinerCommands.REMOTE_HOME : home) + "; "
                + "[ -x $H/bin/RefactoringMiner ] && echo RefactoringMiner "
                + RefactoringMinerCommands.VERSION;
        return new Tool("refactoringminer", "RefactoringMiner",
                "The refactoring badge on a task row and the AST-diff view of what a session "
                        + "restructured. Needs Java on the host.",
                "Downloads RefactoringMiner " + RefactoringMinerCommands.VERSION + " from GitHub into "
                        + "~/.contextswitcher/RefactoringMiner-" + RefactoringMinerCommands.VERSION
                        + " and points the refactoring-badge setting at it. Only ContextSwitcher uses "
                        + "this copy.",
                script(probe), RefactoringMinerCommands.setupCommand(), null);
    }

    static Tool astGrep() {
        String url = "https://github.com/ast-grep/ast-grep/releases/download/" + AST_GREP_VERSION;
        String install = PATH_FIX
                + "A=$(uname -m); case $(uname -s) in Darwin) O=apple-darwin;; *) O=unknown-linux-gnu;; esac; "
                + "mkdir -p $HOME/.local/bin || exit 1; Z=$HOME/.local/ast-grep.zip; U=" + url
                + "/app-$A-$O.zip; "
                + "{ curl -fsSL -o $Z $U || wget -q -O $Z $U; } || exit 1; "
                + "unzip -q -o $Z ast-grep -d $HOME/.local/bin || exit 1; rm -f $Z; "
                + "chmod +x $HOME/.local/bin/ast-grep; "
                + "npx -y skills add ast-grep/agent-skill -g -a claude-code -s '*' -y >/dev/null "
                + "|| echo the ast-grep skills were not installed - no npx? >&2; "
                + "ast-grep --version";
        return new Tool("ast-grep", "ast-grep",
                "Structural code search Claude reaches for instead of grep; installed with its "
                        + "two skills.",
                "Downloads the ast-grep " + AST_GREP_VERSION + " binary from GitHub into "
                        + "~/.local/bin (for this user only, no sudo) and adds its Claude Code skills "
                        + "globally via npx. A system-wide install (package manager, cargo, npm) "
                        + "works as well — install it there and press Recheck.",
                script(PATH_FIX + "ast-grep --version"), script(install), null);
    }

    static Tool codegraph() {
        String install = "mkdir -p $HOME/.claude/skills/codegraph || exit 1; "
                + "cat > $HOME/.claude/skills/codegraph/SKILL.md || exit 1; "
                + "curl -fsSL https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.sh "
                + "| CODEGRAPH_VERSION=" + CODEGRAPH_VERSION + " sh || exit 1; "
                + PATH_FIX + "codegraph install -y >/dev/null 2>&1; codegraph --version";
        return new Tool("codegraph", "codegraph",
                "A call-graph index of each checkout, so Claude reads the symbols it needs "
                        + "instead of grepping; installed with its MCP server and skill.",
                "Runs codegraph's install script (" + CODEGRAPH_VERSION + ") into ~/.local/bin, "
                        + "registers its MCP server with Claude Code (codegraph install) and writes "
                        + "its skill to ~/.claude/skills/codegraph. Indexing a checkout "
                        + "(codegraph init) stays your call.",
                script(PATH_FIX + "codegraph --version"), script(install), codegraphSkill());
    }

    /// `sh -c '<script>'` — the one quoting layer every runner here accepts.
    private static List<String> script(String script) {
        return List.of("sh", "-c", SshCommandRunner.quote(script));
    }

    /// The shipped codegraph skill, written to `~/.claude/skills/codegraph/`
    /// on install — the tool ships none of its own. Line endings are LF
    /// whatever the checkout made of the resource: a Windows clone has CRLF,
    /// and the file lands on a Unix remote.
    static byte[] codegraphSkill() {
        try (InputStream in = RecommendedTools.class.getResourceAsStream("skills/codegraph-SKILL.md")) {
            if (in == null) {
                throw new IOException("codegraph-SKILL.md resource missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// The version a probe or install printed: its last non-empty stdout
    /// line, or null when the command failed.
    public static @Nullable String version(int exitCode, String stdout) {
        if (exitCode != 0) {
            return null;
        }
        return stdout.lines().map(String::strip).filter(line -> !line.isEmpty())
                .reduce((first, last) -> last).orElse(null);
    }
}
