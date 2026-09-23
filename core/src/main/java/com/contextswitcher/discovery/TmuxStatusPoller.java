package com.contextswitcher.discovery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import com.contextswitcher.config.EnergySaver;
import com.contextswitcher.queue.ClaudeMode;
import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Polls the `@cs_status` tmux window user option of every window on the task
/// hosts on a fixed interval, so the task list can show a live running
/// indicator. A Claude session publishes its own state with
/// `tmux set -w @cs_status working|waiting|done`; windows that never set it
/// simply report nothing.
///
/// As a safety net for a status that got stuck (e.g. Claude set `working` but
/// its `Stop` hook never fired), a `working` window whose pane has produced no
/// output for [#IDLE_SECONDS] is reported as `waiting`. The idle age is
/// computed on the remote (from `#{window_activity}` versus the host's own
/// clock) so a Windows/remote clock difference cannot skew it. The threshold
/// is deliberately long so it does not fight correct hook status during a
/// genuinely silent long-running operation; it only ever downgrades `working`.
///
/// One `tmux list-windows` runs per host per tick on a daemon scheduler
/// thread; the merged `host windowId -> status` map is handed to the callback,
/// which is responsible for marshalling to the UI thread.
// [impl->dsn~task-running-indicator~7]
public class TmuxStatusPoller implements AutoCloseable {

    /// A `working` window silent for at least this long is treated as `waiting`.
    static final long IDLE_SECONDS = 15;

    /// Status reported for a window whose pane shows Claude's usage-limit
    /// message — the session is blocked until the user picks another model or
    /// buys credits, which no hook announces.
    // [impl->dsn~claude-limit-detection~2]
    public static final String LIMIT = "limit";

    /// `grep -E` pattern matching Claude Code's limit messages: the classic
    /// "Claude usage limit reached …" and the newer "You've reached your
    /// <model> limit. Run /usage-credits …".
    // ponytail: grep over the last lines of the pane; replace with a published
    // `@cs_limit` option if Claude Code ever fires a hook for the limit.
    private static final String LIMIT_PATTERN = "limit reached|reached your .{0,40}limit";

    /// Marker prefix of the pane grep for Claude's transient API errors — the
    /// ones that end a turn mid-response ("API Error: Connection lost
    /// mid-response.").
    // [impl->dsn~api-error-auto-continue~1]
    static final String API_ERROR = "apierror";

    /// `grep -E` pattern matching those errors.
    private static final String API_ERROR_PATTERN = "API Error";

    /// A window that keeps showing the error gets a new `continue` at most
    /// this often — the error stays in the pane after a nudge that did not
    /// take, and one nudge per five-second tick would be a flood.
    // [impl->dsn~api-error-auto-continue~1]
    static final long CONTINUE_COOLDOWN_SECONDS = 300;

    /// Marker prefix of the pane grep for Claude Code's pending self-update —
    /// the "Update installed · Restart to update" line it shows in its footer
    /// once the new version is unpacked but the running process is still the
    /// old one.
    // [impl->dsn~claude-update-restart~6]
    static final String UPDATE_PENDING = "update";

    /// Marker prefix of the windows an attached tmux client shows right now —
    /// the app's terminal mirror or the user's own terminal: someone is
    /// looking at, and may be typing into, that window.
    // [impl->dsn~claude-update-restart~6]
    static final String VIEWED = "viewed";

    /// `grep -E` pattern matching that footer line.
    private static final String UPDATE_PATTERN = "Restart to update";

    /// Whether a captured screen shows the pending-update footer: the same
    /// rule the remote grep applies, for a caller that already holds the
    /// screen (the phone's terminal page) — the bottom four non-blank lines,
    /// SGR escapes removed, never the screen above them.
    // [impl->dsn~android-claude-restart~1]
    public static boolean updatePendingIn(String screen) {
        List<String> lines = screen.replaceAll("\u001B\\[[0-9;?]*[A-Za-z]", "").lines()
                .filter(line -> !line.isBlank()).toList();
        return lines.subList(Math.max(0, lines.size() - 4), lines.size()).stream()
                .anyMatch(line -> line.contains(UPDATE_PATTERN));
    }

    /// A window whose restart did not take is retried at most this often —
    /// the footer keeps the message until a Claude that has it really comes
    /// up, so a per-tick retry would quit the session over and over.
    // [impl->dsn~claude-update-restart~6]
    static final long RESTART_COOLDOWN_SECONDS = 300;

    /// How many `/exit`s a window gets for one pending update before it is
    /// left alone: one that does not quit is busy with something the status
    /// does not show (field report 2026-09-14, a `done` window took a dozen
    /// `/exit`s as prompts — its background shells' question is answered by
    /// [#exitCommand] now; this guards whatever else keeps a session up).
    // [impl->dsn~claude-update-restart~6]
    static final int MAX_RESTART_ATTEMPTS = 2;

    private final SshCommandRunner ssh;
    private final Supplier<Set<String>> hosts;
    private final Consumer<Map<String, String>> onUpdate;
    /// Fed the same tick's `@cs_model`/`@cs_effort` per window — the session's
    /// own report of what it currently answers with.
    // [impl->dsn~claude-mode-report~2]
    private final Consumer<Map<String, ClaudeMode>> onModes;
    /// Fed the same tick's `@cs_session_id` per window, so a task's recorded
    /// window can be checked against the session it really hosts without a
    /// round-trip of its own.
    // [impl->dsn~tmux-window-ownership~4]
    private final Consumer<Map<String, String>> onSessionIds;
    /// Fed the same tick's `#{pane_current_command}` per window — what the
    /// window's active pane is actually running, so a window whose Claude
    /// never came up (or died) can be told from one that hosts a session.
    // [impl->dsn~start-claude-button~2]
    private final Consumer<Map<String, String>> onCommands;
    /// Fed the same tick's idle seconds per status-publishing window — the
    /// auto-suspend's "inactive for how long" (`dsn~auto-suspend-idle~2`).
    // [impl->dsn~auto-suspend-idle~2]
    private final Consumer<Map<String, Long>> onIdle;
    private final long intervalSeconds;
    /// Window key -> epoch second of the last auto-`continue` sent to it;
    /// dropped again once the window reports `working`. Poller thread only.
    // [impl->dsn~api-error-auto-continue~1]
    private final Map<String, Long> continued = new HashMap<>();
    /// Window key -> epoch second of the last update restart typed into it.
    /// Poller thread only.
    // [impl->dsn~claude-update-restart~6]
    private final Map<String, Long> restarted = new HashMap<>();
    /// Window key -> the session id to resume once the window's `/exit` has
    /// taken, i.e. its pane no longer runs `claude` (a blank id resumes by
    /// `--continue`). Poller thread only.
    // [impl->dsn~claude-update-restart~6]
    private final Map<String, String> resumePending = new HashMap<>();
    /// Window key -> `/exit`s sent for the update it currently shows; cleared
    /// when the marker is gone. Poller thread only.
    // [impl->dsn~claude-update-restart~6]
    private final Map<String, Integer> restartAttempts = new HashMap<>();
    /// Whether a restarted session gets `--dangerously-skip-permissions` back
    /// (settings `claudeAuto`) — the flag is the process's, so a restart drops
    /// it unless it is typed again.
    // [impl->dsn~claude-update-restart~6]
    private final boolean claudeAuto;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tmux-status-poller");
        thread.setDaemon(true);
        return thread;
    });

    public TmuxStatusPoller(SshCommandRunner ssh, Supplier<Set<String>> hosts,
            Consumer<Map<String, String>> onUpdate, Consumer<Map<String, ClaudeMode>> onModes,
            Consumer<Map<String, String>> onSessionIds, Consumer<Map<String, String>> onCommands,
            Consumer<Map<String, Long>> onIdle, boolean claudeAuto, long intervalSeconds) {
        this.ssh = ssh;
        this.hosts = hosts;
        this.onUpdate = onUpdate;
        this.onModes = onModes;
        this.onSessionIds = onSessionIds;
        this.onCommands = onCommands;
        this.onIdle = onIdle;
        this.claudeAuto = claudeAuto;
        this.intervalSeconds = intervalSeconds;
    }

    /// Starts polling. Uses fixed-*delay* so a slow tmux/ssh round-trip never
    /// lets ticks pile up on each other.
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /// The periodic tick: skipped whole while the energy saver is on, so the
    /// poller costs nothing until a refresh asks for it.
    // [impl->dsn~energy-saver~1]
    private void tick() {
        if (!EnergySaver.active()) {
            poll();
        }
    }

    /// Runs one poll right now on the poller thread, past the energy-saver
    /// gate — the manual refresh.
    // [impl->dsn~energy-saver~1]
    public CompletableFuture<Void> pollNow() {
        return CompletableFuture.runAsync(this::poll, scheduler);
    }

    private void poll() {
        Map<String, String> merged = new HashMap<>();
        Map<String, ClaudeMode> modes = new HashMap<>();
        Map<String, String> sessionIds = new HashMap<>();
        Map<String, String> commands = new HashMap<>();
        Map<String, Long> idle = new HashMap<>();
        for (String host : hosts.get()) {
            try {
                SshCommandRunner.SshResult result = ssh.run(host, statusCommand());
                if (result.ok()) {
                    parseInto(host, result.stdout(), merged);
                    parseModesInto(host, result.stdout(), modes);
                    parseSessionIdsInto(host, result.stdout(), sessionIds);
                    parseCommandsInto(host, result.stdout(), commands);
                    parseIdleInto(host, result.stdout(), idle);
                    autoContinue(host, result.stdout(), merged);
                    autoRestart(host, result.stdout(), merged, sessionIds, commands);
                }
            } catch (Exception e) {
                Logger.debug("Status poll failed for {}: {}", host, e.getMessage());
            }
        }
        onUpdate.accept(Map.copyOf(merged));
        onModes.accept(Map.copyOf(modes));
        onSessionIds.accept(Map.copyOf(sessionIds));
        onCommands.accept(Map.copyOf(commands));
        onIdle.accept(Map.copyOf(idle));
    }

    /// Sends `continue` to every window of `host` whose pane tail shows one of
    /// Claude's API errors and that is not working: the turn ended
    /// mid-response, and the session sits there until someone types the word.
    /// A window that reports `working` again clears its cooldown, so a later
    /// error is nudged right away.
    // [impl->dsn~api-error-auto-continue~1]
    void autoContinue(String host, String stdout, Map<String, String> statuses) {
        statuses.forEach((key, status) -> {
            if ("working".equalsIgnoreCase(status)) {
                continued.remove(key);
            }
        });
        long now = System.currentTimeMillis() / 1000;
        for (String windowId : parseMarked(stdout, API_ERROR)) {
            String key = key(host, windowId);
            String status = statuses.get(key);
            // No status this tick means the window did not answer at all —
            // nothing to nudge into.
            if (status == null || "working".equalsIgnoreCase(status)) {
                continue;
            }
            Long last = continued.get(key);
            if (last != null && now - last < CONTINUE_COOLDOWN_SECONDS) {
                continue;
            }
            Logger.info("API error in {} {} — sending continue", host, windowId);
            continued.put(key, now);
            try {
                ssh.run(host, continueCommand(windowId));
            } catch (Exception e) {
                Logger.debug("Auto-continue failed for {} {}: {}", host, windowId, e.getMessage());
            }
        }
    }

    /// Types `continue` into the window and submits it a second later — the
    /// same split as the queue send (`QueueSendCommands.pasteCommand`), since
    /// an `Enter` in the same burst is absorbed as a newline by Claude's input
    /// box. `-l` sends the word literally instead of looking it up as a key
    /// name.
    // [impl->dsn~api-error-auto-continue~1]
    static List<String> continueCommand(String windowId) {
        return List.of("tmux", "send-keys", "-l", "-t", "'" + windowId + "'", "continue", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", "'" + windowId + "'", "Enter");
    }

    /// One remote shell line: snapshot the host clock, then print
    /// `windowId|status|idleSeconds` per window (idle = now − last pane
    /// activity, computed remotely). The tmux format is single-quoted so the
    /// remote shell leaves `#{…}` and `|` intact; `#{@cs_status}` expands to
    /// empty for windows that never set it. The awk program joins with
    /// `OFS=FS` instead of `"|"` literals because a remote command must not
    /// contain double quotes (see `ProcessSshRunner.buildCommand`).
    /// The trailing `#{@cs_model}`/`#{@cs_effort}`/`#{@cs_session_id}` ride
    /// along on the same round-trip (`dsn~claude-mode-report~2`,
    /// `dsn~tmux-window-ownership~4`) rather than costing a poller of their
    /// own — one `list-windows` per host per tick either way.
    ///
    /// A second pass greps the tail of every status-publishing window's pane
    /// for Claude's usage-limit message and emits a `limit|windowId` marker
    /// line per hit (`dsn~claude-limit-detection~2`); `#{?@cs_status,…,}`
    /// keeps the `capture-pane` calls to the Claude windows. The trailing
    /// `true` keeps the exit code at 0 when the last window did not match.
    /// A third grep looks at the bottom four non-blank lines — Claude's
    /// footer, below the input box — for the pending self-update and emits
    /// an `update|windowId` marker (`dsn~claude-update-restart~6`). Not the
    /// whole screen: a diff Claude shows can quote the phrase (2026-09-15,
    /// JabRef's *Restart to update* button label on screen got a session
    /// its `/exit`).
    // [impl->dsn~claude-limit-detection~2]
    // [impl->dsn~claude-update-restart~6]
    // [impl->dsn~android-finish-notification~1]
    public static List<String> statusCommand() {
        return List.of("now=$(date +%s); "
                + "tmux list-windows -a "
                + "-F '#{window_id}|#{@cs_status}|#{window_activity}|#{@cs_model}|#{@cs_effort}"
                + "|#{@cs_session_id}|#{pane_current_command}' "
                + "| awk -F'|' -v n=$now 'BEGIN{OFS=FS} {print $1,$2,n-$3,$4,$5,$6,$7}'; "
                + "for w in $(tmux list-windows -a -F '#{?@cs_status,#{window_id},}'); do "
                + "tmux capture-pane -p -J -S -12 -t $w 2>/dev/null "
                + "| grep -qE '" + LIMIT_PATTERN + "' && echo '" + LIMIT + "|'$w; "
                + "tmux capture-pane -p -J -t $w 2>/dev/null | grep -v '^ *$' | tail -8 "
                + "| grep -qE '" + API_ERROR_PATTERN + "' && echo '" + API_ERROR + "|'$w; "
                + "tmux capture-pane -p -J -t $w 2>/dev/null | grep -v '^ *$' | tail -4 "
                + "| grep -qE '" + UPDATE_PATTERN + "' && echo '" + UPDATE_PENDING + "|'$w; "
                + "done; "
                + "tmux list-clients -F '" + VIEWED + "|#{window_id}' 2>/dev/null; true");
    }

    /// Quits Claude in every window of `host` whose footer shows the pending
    /// self-update and whose turn is over (`waiting` or `done`) — an update
    /// only takes effect in a fresh process — and resumes the conversation in
    /// a later tick, once the window's pane no longer runs `claude`.
    ///
    /// Two phases rather than a blind `sleep` between them: a window whose
    /// `/exit` did not take (busy with what the status does not show) got the
    /// resume line typed into its input box as a prompt, and again every
    /// cooldown. A window gets [#MAX_RESTART_ATTEMPTS] `/exit`s per update.
    ///
    /// `sessionIds` is the same tick's `@cs_session_id` per window, so the
    /// window comes back up on **its own** conversation ([#resumeCommand]);
    /// `commands` the same tick's `#{pane_current_command}`.
    // [impl->dsn~claude-update-restart~6]
    void autoRestart(String host, String stdout, Map<String, String> statuses,
            Map<String, String> sessionIds, Map<String, String> commands) {
        long now = System.currentTimeMillis() / 1000;
        Set<String> pending = parseMarked(stdout, UPDATE_PENDING);
        // Not while someone looks at the window: the Escape would throw away
        // what they are typing and the /exit end the session under their eyes.
        // The next tick after they moved on restarts it.
        Set<String> viewed = parseMarked(stdout, VIEWED);
        // First the windows quit in an earlier tick (before this tick's exits,
        // so a fresh `/exit` is never judged by the same tick's command). The
        // window is heard from and its Claude is gone: resume.
        // Still `claude`: the exit has not taken; the cooldown decides whether
        // it is tried again, and the pending resume is dropped so a fresh
        // `/exit` never gets its resume typed into a running session.
        for (Iterator<Map.Entry<String, String>> it = resumePending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, String> entry = it.next();
            String key = entry.getKey();
            if (!key.startsWith(host + " ")) {
                continue;
            }
            String command = commands.get(key);
            if (command == null) {
                continue;
            }
            it.remove();
            String windowId = key.substring(host.length() + 1);
            if ("claude".equals(command)) {
                Logger.warn("Claude in {} {} did not quit on /exit — not resuming; {} of {} attempts",
                        host, windowId, restartAttempts.getOrDefault(key, 0), MAX_RESTART_ATTEMPTS);
                continue;
            }
            Logger.info("Claude left {} {} — resuming the session", host, windowId);
            try {
                ssh.run(host, resumeCommand(windowId, entry.getValue(), claudeAuto));
            } catch (Exception e) {
                Logger.debug("Update resume failed for {} {}: {}", host, windowId, e.getMessage());
            }
        }
        for (String windowId : pending) {
            String key = key(host, windowId);
            if (resumePending.containsKey(key) || !restartable(statuses.get(key)) || viewed.contains(windowId)) {
                continue;
            }
            Long last = restarted.get(key);
            if (last != null && now - last < RESTART_COOLDOWN_SECONDS) {
                continue;
            }
            int attempts = restartAttempts.getOrDefault(key, 0);
            if (attempts >= MAX_RESTART_ATTEMPTS) {
                continue;
            }
            String sessionId = sessionIds.get(key);
            Logger.info("Claude update pending in {} {} — quitting the session ({}), attempt {}",
                    host, windowId, sessionId == null ? "no published session id" : sessionId,
                    attempts + 1);
            restarted.put(key, now);
            restartAttempts.put(key, attempts + 1);
            try {
                ssh.run(host, exitCommand(windowId));
                resumePending.put(key, sessionId == null ? "" : sessionId);
            } catch (Exception e) {
                Logger.debug("Update exit failed for {} {}: {}", host, windowId, e.getMessage());
            }
        }
        // The footer no longer shows the update: the next one starts afresh.
        restartAttempts.keySet().removeIf(key -> key.startsWith(host + " ")
                && !pending.contains(key.substring(host.length() + 1)));
    }

    /// Whether a window in this status may be restarted: only one whose turn
    /// is over. `working` is mid-answer, `attention` has a question or a
    /// permission prompt on screen that the restart would throw away, `limit`
    /// is not helped by a new process, and a window with no status this tick
    /// did not answer at all.
    // [impl->dsn~claude-update-restart~6]
    public static boolean restartable(@Nullable String status) {
        return "waiting".equalsIgnoreCase(status) || "done".equalsIgnoreCase(status);
    }

    /// Clears the input box (a draft would swallow the `/exit` and submit it
    /// as a prompt), types `/exit`, submits it a second later — the same
    /// split as [#continueCommand] — and presses `Enter` once more another
    /// second on: a session with background shells answers `/exit` with a
    /// "Background work is running" question whose preselected choice is
    /// *Exit and stop tasks* (field report 2026-09-14), and the second
    /// `Enter` confirms it. Without the question the same `Enter` lands on
    /// the shell prompt Claude left behind, or in an empty input box —
    /// nothing either way. The sleeps are server-side `run-shell`s, so the
    /// whole thing is one round-trip.
    // [impl->dsn~claude-update-restart~6]
    public static List<String> exitCommand(String windowId) {
        String target = "'" + windowId + "'";
        return List.of("tmux", "send-keys", "-t", target, "Escape", "\\;",
                "send-keys", "-l", "-t", target, "/exit", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", target, "Enter", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", target, "Enter");
    }

    /// Resumes, at the shell prompt Claude left behind, the conversation that
    /// just ended — sent only once the pane no longer runs `claude`.
    ///
    /// Resumed **by id** (`claude --resume <@cs_session_id>`, the window's own
    /// published session, like `TmuxResurrect.startCommand`), not with
    /// `claude --continue`: `--continue` means "the most recent conversation
    /// in this directory", and tasks routinely share one Claude cwd (a
    /// category's workspaces root, with a git worktree per task). Field report
    /// 2026-09-13: a restarted window came back holding a **neighbouring**
    /// task's conversation — its terminal showed foreign work, messages queued
    /// for it were delivered into that other chat, and with two windows then
    /// publishing one session id `Main.uniqueSessionIds` dropped the id for
    /// ownership (`dsn~tmux-window-ownership~4`), so the task auto-suspended.
    ///
    /// `--continue` stays as the `||` fallback for a window that published no
    /// id, and for an id the remote no longer knows — no worse than what it
    /// alone used to do, and the only way back into *some* conversation.
    // [impl->dsn~claude-update-restart~6]
    public static List<String> resumeCommand(String windowId, @Nullable String sessionId, boolean auto) {
        String target = "'" + windowId + "'";
        String flags = auto ? " --dangerously-skip-permissions" : "";
        String fallback = "claude --continue" + flags;
        String resume = "'" + (sessionId == null || sessionId.isBlank()
                ? fallback
                : "claude --resume " + sessionId + flags + " || " + fallback) + "'";
        return List.of("tmux", "send-keys", "-t", target, resume, "Enter");
    }

    /// Parses `windowId|status|idleSeconds` lines, adding `host windowId ->
    /// status` for every window whose status is non-empty. A `working` window
    /// idle for at least [#IDLE_SECONDS] is reported as `waiting`, and a window
    /// listed by a `limit|windowId` marker line reports [#LIMIT] unless it is
    /// genuinely `working` again (the message is then stale scrollback).
    static void parseInto(String host, String stdout, Map<String, String> out) {
        parseInto(host, stdout, out, IDLE_SECONDS);
    }

    /// As [#parseInto(String, String, Map)], with the silence after which a
    /// `working` window counts as `waiting` given by the caller: the phone
    /// notifies on that change, and a quiet build step must not read as
    /// "Claude finished" there (`dsn~android-finish-notification~1`).
    // [impl->dsn~android-finish-notification~1]
    public static void parseInto(String host, String stdout, Map<String, String> out, long idleThresholdSeconds) {
        Set<String> limited = parseLimited(stdout);
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 6);
            if (parts.length < 3) {
                continue;
            }
            String status = parts[1].strip();
            if (!status.isEmpty()) {
                String effective = effectiveStatus(status, parseIdle(parts[2]), idleThresholdSeconds);
                if (limited.contains(parts[0]) && !"working".equalsIgnoreCase(effective)) {
                    effective = LIMIT;
                }
                out.put(key(host, parts[0]), effective);
            }
        }
    }

    /// The window ids of the `limit|windowId` marker lines the pane grep emits.
    // [impl->dsn~claude-limit-detection~2]
    /// The window ids whose footer shows "Restart to update" (the `update|windowId` marker lines).
    // [impl->dsn~android-claude-restart~1]
    public static Set<String> parseUpdatePending(String stdout) {
        return parseMarked(stdout, UPDATE_PENDING);
    }

    static Set<String> parseLimited(String stdout) {
        return parseMarked(stdout, LIMIT);
    }

    /// The window ids of the `<marker>|windowId` lines the pane greps emit.
    static Set<String> parseMarked(String stdout, String marker) {
        Set<String> marked = new HashSet<>();
        for (String line : stdout.split("\\R")) {
            if (line.startsWith(marker + "|")) {
                marked.add(line.substring(marker.length() + 1).strip());
            }
        }
        return marked;
    }

    /// Parses the same lines for `windowId|…|model|effort`, adding an entry for
    /// every window that reports at least one of the two. Unlike the status,
    /// nothing is inferred: a window that publishes nothing simply has no entry,
    /// and the UI falls back to "as is".
    // [impl->dsn~claude-mode-report~2]
    static void parseModesInto(String host, String stdout, Map<String, ClaudeMode> out) {
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 6);
            if (parts.length < 5) {
                continue;
            }
            ClaudeMode mode = new ClaudeMode(blankToNull(parts[3]), blankToNull(parts[4]));
            if (!mode.equals(ClaudeMode.DEFAULT)) {
                out.put(key(host, parts[0]), mode);
            }
        }
    }

    /// Parses the same lines for the trailing `@cs_session_id`, adding an entry
    /// per window that published one. A window that publishes nothing has no
    /// entry rather than an empty one, so a reader can tell "hosts no known
    /// Claude session" from "did not answer" (`dsn~tmux-window-ownership~4`).
    // [impl->dsn~tmux-window-ownership~4]
    static void parseSessionIdsInto(String host, String stdout, Map<String, String> out) {
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 7);
            if (parts.length < 6) {
                continue;
            }
            String sessionId = parts[5].strip();
            if (!sessionId.isEmpty()) {
                out.put(key(host, parts[0]), sessionId);
            }
        }
    }

    /// Parses the same lines for the trailing `#{pane_current_command}`,
    /// adding an entry per window that reported one. Absent means "did not
    /// answer"; `claude` means the pane really runs a Claude session — the
    /// same test `TmuxTaskImporter` uses on import.
    // [impl->dsn~start-claude-button~2]
    static void parseCommandsInto(String host, String stdout, Map<String, String> out) {
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 7);
            if (parts.length < 7) {
                continue;
            }
            String command = parts[6].strip();
            if (!command.isEmpty()) {
                out.put(key(host, parts[0]), command);
            }
        }
    }

    /// Parses the same lines for the idle seconds of every window that
    /// publishes a status — the windows Claude runs in; a plain shell's
    /// silence means nothing.
    // [impl->dsn~auto-suspend-idle~2]
    static void parseIdleInto(String host, String stdout, Map<String, Long> out) {
        for (String line : stdout.split("\\R")) {
            String[] parts = line.split("\\|", 6);
            if (parts.length >= 3 && !parts[1].isBlank()) {
                out.put(key(host, parts[0]), parseIdle(parts[2]));
            }
        }
    }

    private static @Nullable String blankToNull(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /// Downgrades a stale `working` to `waiting`; every other status is kept.
    static String effectiveStatus(String status, long idleSeconds) {
        return effectiveStatus(status, idleSeconds, IDLE_SECONDS);
    }

    private static String effectiveStatus(String status, long idleSeconds, long idleThresholdSeconds) {
        if ("working".equalsIgnoreCase(status) && idleSeconds >= idleThresholdSeconds) {
            return "waiting";
        }
        return status;
    }

    private static long parseIdle(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /// The map key identifying a window across hosts.
    public static String key(String host, String windowId) {
        return host + " " + windowId;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
