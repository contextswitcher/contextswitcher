package com.contextswitcher.discovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.terminal.TmuxMirrorCommands;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Lists running tmux sessions (with their windows) on a remote host.
// [impl->dsn~tmux-task-import~12]
public class TmuxDiscovery {

    /// `id` is the immutable tmux window id (`@17`) — the stable reference;
    /// `index` is only a human-readable snapshot (it shifts on renumbering).
    /// `cwd`/`command` describe the active pane. `paneTitle` is the active
    /// pane's title when it carries information (Claude Code publishes its
    /// task summary there; the tmux default — the hostname — is filtered to
    /// empty). `workspace` and `status` are the `@cs_workspace`/`@cs_status`
    /// tmux user options a Claude session publishes for itself (empty when
    /// unset). `sessionId` is the exact id from the `@cs_session_id` user
    /// option (published by Claude Code's `SessionStart` hook) when set;
    /// otherwise a [ClaudeSessionLookup] enrichment step guesses it for
    /// `claude` windows from the newest transcript of the cwd's project —
    /// a heuristic that goes wrong when two sessions started in the same
    /// directory. `commit` is the `@cs_commit` user option — the last commit
    /// the session published for itself (empty when unset).
    public record TmuxWindow(String id, String index, String cwd, String command, String name,
            String paneTitle, String workspace, String status, @Nullable String sessionId,
            @Nullable String prUrl, String commit) {

        /// Back-compat constructor without `commit` (empty — option unset).
        public TmuxWindow(String id, String index, String cwd, String command, String name,
                String paneTitle, String workspace, String status, @Nullable String sessionId,
                @Nullable String prUrl) {
            this(id, index, cwd, command, name, paneTitle, workspace, status, sessionId, prUrl, "");
        }

        public TmuxWindow withSessionId(String newSessionId) {
            return new TmuxWindow(id, index, cwd, command, name, paneTitle, workspace, status,
                    newSessionId, prUrl, commit);
        }

        public TmuxWindow withPrUrl(String newPrUrl) {
            return new TmuxWindow(id, index, cwd, command, name, paneTitle, workspace, status,
                    sessionId, newPrUrl, commit);
        }
    }

    public record TmuxSession(String name, List<TmuxWindow> windows) {
    }

    /// The format string is wrapped in single quotes so the remote shell
    /// neither treats `#{…}` as a comment nor `|` as a pipe. The window name
    /// comes last — it is the field most likely to contain `|`. `#{host}`
    /// exists only to recognize a default pane title. `@cs_workspace`
    /// and `@cs_status` expand to empty when the window has not set them.
    private static final String FORMAT =
            "'#{session_name}|#{window_id}|#{window_index}|#{pane_current_path}|#{pane_current_command}"
            + "|#{@cs_workspace}|#{@cs_status}|#{@cs_session_id}|#{@cs_pr}|#{@cs_commit}"
            + "|#{host}|#{pane_title}|#{window_name}'";

    private final SshCommandRunner ssh;

    public TmuxDiscovery(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    public static List<String> remoteCommand() {
        return List.of("tmux", "list-windows", "-a", "-F", FORMAT);
    }

    public List<TmuxSession> listSessions(String host) throws IOException {
        SshCommandRunner.SshResult result = ssh.run(host, remoteCommand());
        if (!result.ok()) {
            String detail = result.stderr().isBlank() ? "ssh exit " + result.exitCode() : result.stderr().strip();
            throw new IOException("Cannot list tmux sessions on %s: %s".formatted(host, detail));
        }
        List<TmuxSession> sessions = parse(result.stdout());
        int windows = sessions.stream().mapToInt(session -> session.windows().size()).sum();
        Logger.trace("tmux discovery on {}: {} chars stdout, {} sessions, {} windows",
                host, result.stdout().length(), sessions.size(), windows);
        if (windows == 0 && !result.stdout().isBlank()) {
            Logger.warn("tmux discovery parsed no window from non-empty output; first line: {}",
                    result.stdout().lines().findFirst().orElse(""));
        }
        return sessions;
    }

    /// Parses `session|windowId|windowIndex|cwd|command|workspace|status|sessionId|prUrl|host|paneTitle|windowName`
    /// lines, preserving session order. A pane title equal to the host name
    /// is tmux's default — no information, normalized to empty.
    static List<TmuxSession> parse(String output) {
        Map<String, List<TmuxWindow>> bySession = new LinkedHashMap<>();
        // Mirror lines are held back rather than dropped: a grouped mirror
        // *shares* the base session's windows, so normally each one is listed
        // twice and the base copy is the one to keep — but when the base
        // session is gone the mirror is the only session still holding them,
        // and dropping its lines makes live windows vanish from the listing.
        // Field report 2026-09-12: `old-group` died, `cs-mirror-old-group`
        // survived with its 8 windows, discovery reported "2 sessions, 6
        // windows" and the reconcile suspended all eight tasks — whose
        // windows the app was talking to at that very moment.
        // [impl->dsn~stranded-mirror-windows~1]
        List<String[]> mirrored = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 13);
            if (parts.length < 13) {
                continue;
            }
            if (parts[0].startsWith(TmuxMirrorCommands.MIRROR_PREFIX)) {
                mirrored.add(parts);
                continue;
            }
            addWindow(bySession, parts[0], parts);
        }
        Set<String> known = bySession.values().stream().flatMap(List::stream)
                .map(TmuxWindow::id).collect(Collectors.toSet());
        for (String[] parts : mirrored) {
            if (known.add(parts[1])) {
                // Under the base session's name — what the tasks carry, and
                // what a re-grouping attach addresses.
                // ponytail: de-prefixing is not the exact inverse of
                // `mirrorName` (which sanitizes non-alphanumerics), so a
                // session whose name needed sanitizing comes back with
                // underscores. Harmless where the name only labels a session
                // that is already gone; read `#{session_group}` if it ever
                // has to be exact.
                addWindow(bySession,
                        parts[0].substring(TmuxMirrorCommands.MIRROR_PREFIX.length()), parts);
            }
        }
        return bySession.entrySet().stream()
                .map(entry -> new TmuxSession(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    /// Braille patterns (U+2800–U+28FF) plus common spinner characters
    /// (`*`, `·`, `•`, `✳`, `✴`, `✵`, `✶`, `✻`), leading only.
    private static final String SPINNER_PREFIX =
            "^[\\s\\u2800-\\u28FF*\\u00B7\\u2022\\u2733\\u2734\\u2735\\u2736\\u273B]+";

    /// A raw tmux pane title, normalized: the tmux default (the host name) and
    /// blanks become empty — no information; leading spinner glyphs that Claude
    /// Code animates in front of its task summary while working (braille
    /// patterns like `⠂`, plus asterisk/bullet variants) are stripped, so a
    /// title captured mid-spin does not leak the animation frame into task
    /// titles or the snapshot header.
    static String cleanPaneTitle(String rawTitle, String host) {
        if (rawTitle.equals(host)) {
            return "";
        }
        return rawTitle.replaceFirst(SPINNER_PREFIX, "").strip();
    }

    /// One parsed line into its session bucket.
    private static void addWindow(Map<String, List<TmuxWindow>> bySession, String session,
            String[] parts) {
        String paneTitle = cleanPaneTitle(parts[11], parts[10]);
        String sessionId = parts[7].isBlank() ? null : parts[7].strip();
        String prUrl = parts[8].isBlank() ? null : parts[8].strip();
        bySession.computeIfAbsent(session, name -> new ArrayList<>())
                .add(new TmuxWindow(parts[1], parts[2], parts[3], parts[4], parts[12],
                        paneTitle, parts[5], parts[6], sessionId, prUrl, parts[9]));
    }
}
