package com.contextswitcher.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Scrapes the task's pull-request URL from a Claude window's pane content:
/// sessions print a footer like `… · PR: https://github.com/owner/repo/pull/16246`.
/// The labeled form (`PR:`) is required so PR links merely mentioned in the
/// conversation do not match. **Every** distinct match is returned: one Claude
/// session can open several PRs at once (a code PR plus its documentation PR),
/// and the footer then lists one `PR:` line per repository.
/// The publishable `@cs_pr` tmux option takes precedence over scraping
/// (https://github.com/contextswitcher/contextswitcher-private/issues/46's mechanism); this lookup covers sessions that only show the
/// footer.
// [impl->dsn~claude-pr-capture~2]
public class ClaudePrLookup {

    private static final Pattern PR_FOOTER =
            Pattern.compile("PR:\\s*(https://github\\.com/[\\w.-]+/[\\w.-]+/pull/\\d+)");

    /// Separator the batch command prints before each window's pane text —
    /// distinctive enough that no pane content produces it by accident.
    static final String MARKER_PREFIX = "===contextswitcher-pane ";
    private static final String MARKER_SUFFIX = "===";
    private static final Pattern MARKER = Pattern.compile(
            "^" + Pattern.quote(MARKER_PREFIX) + "(\\S+)" + Pattern.quote(MARKER_SUFFIX) + "$");

    private final SshCommandRunner ssh;

    public ClaudePrLookup(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// Recent pane content including scrollback; enough lines to catch the
    /// footer under Claude's TUI redraws.
    static List<String> remoteCommand(String windowId) {
        return List.of("tmux", "capture-pane", "-p", "-J", "-S", "-100", "-t", "'" + windowId + "'");
    }

    /// All windows of one host in **one** ssh round-trip: `echo '<marker @5>';
    /// tmux capture-pane … -t '@5'; echo '<marker @24>'; …`. One connection
    /// per window instead — 40 Claude windows every 15 s — was a burst of
    /// 40 simultaneous `ssh.exe` processes: sshd's `MaxStartups` refused most
    /// of them, and the local process storm starved the FX thread.
    /// `;` rather than `&&`: a window that is gone must not skip the rest.
    static List<String> batchCommand(Collection<String> windowIds) {
        List<String> command = new ArrayList<>();
        for (String windowId : windowIds) {
            if (!command.isEmpty()) {
                command.add(";");
            }
            command.add("echo");
            command.add(SshCommandRunner.quote(MARKER_PREFIX + windowId + MARKER_SUFFIX));
            command.add(";");
            command.addAll(remoteCommand(windowId));
        }
        return command;
    }

    /// Splits the batch output at the markers and scrapes each section:
    /// window id → its distinct PR URLs (windows without any are absent).
    static Map<String, List<String>> parseBatch(String stdout) {
        Map<String, List<String>> urls = new LinkedHashMap<>();
        @Nullable String current = null;
        StringBuilder section = new StringBuilder();
        for (String line : stdout.split("\\R", -1)) {
            Matcher marker = MARKER.matcher(line);
            if (marker.matches()) {
                addSection(urls, current, section);
                current = marker.group(1);
                section.setLength(0);
            } else {
                section.append(line).append('\n');
            }
        }
        addSection(urls, current, section);
        return urls;
    }

    private static void addSection(Map<String, List<String>> urls, @Nullable String windowId,
            StringBuilder section) {
        if (windowId == null) {
            return;
        }
        List<String> found = parsePrUrls(section.toString());
        if (!found.isEmpty()) {
            urls.put(windowId, found);
        }
    }

    /// The distinct PR URLs in the pane, in the order they appear. A redrawn
    /// TUI repeats the same footer many times, hence the deduplication.
    static List<String> parsePrUrls(String paneText) {
        Matcher matcher = PR_FOOTER.matcher(paneText);
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        return List.copyOf(urls);
    }

    /// The footer PR URLs of every given window on `host`, one ssh call
    /// ([#batchCommand]). The exit code is the last capture's, so a gone
    /// window must not discard the others: only a failure that left no
    /// output at all (the connection itself) is treated as one.
    public Map<String, List<String>> findPrUrls(String host, Collection<String> windowIds) {
        if (windowIds.isEmpty()) {
            return Map.of();
        }
        SshCommandRunner.SshResult result = ssh.run(host, batchCommand(windowIds));
        if (!result.ok() && result.stdout().isBlank()) {
            Logger.debug("Cannot capture {} panes on {} for the PR footer: {}",
                    windowIds.size(), host, result.stderr().strip());
            return Map.of();
        }
        return parseBatch(result.stdout());
    }
}
