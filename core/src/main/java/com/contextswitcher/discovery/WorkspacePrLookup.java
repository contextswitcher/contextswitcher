package com.contextswitcher.discovery;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Asks `gh` in the task's workspace which pull request the checked-out branch
/// belongs to. This is the capture path for a session working on an
/// **existing** PR — reviewing it, fixing it up, editing its body: such a
/// session neither publishes `@cs_pr` nor prints the `PR:` footer
/// [ClaudePrLookup] needs, so without this the task stays link-less although
/// its branch has had a PR all along.
///
/// The branch is the authoritative source here, which is why the pane is not
/// scraped for bare PR URLs instead: a Claude pane routinely shows PR links
/// from a changelog, an issue, or a review of some other repository, and every
/// one of them would land in the task file.
///
/// `gh` runs where the workspace is: locally when the workspace path exists
/// on this machine (ContextSwitcher and the session on the same host), else
/// over ssh. Local first because ssh is the part that breaks — a dropped
/// connection turned every lookup into an `Exit 255` warning although the
/// checkout was right there.
// [impl->dsn~claude-pr-refresh~5]
public class WorkspacePrLookup {

    private static final Pattern URL = Pattern.compile(
            "\"url\"\\s*:\\s*\"(https://github\\.com/[\\w.-]+/[\\w.-]+/pull/\\d+)\"");

    /// Separator the batch command prints before each workspace's `gh` output.
    static final String MARKER_PREFIX = "===contextswitcher-workspace ";
    private static final String MARKER_SUFFIX = "===";
    private static final Pattern MARKER = Pattern.compile(
            "^" + Pattern.quote(MARKER_PREFIX) + "(.+)" + Pattern.quote(MARKER_SUFFIX) + "$");

    private final SshCommandRunner ssh;
    private final LocalCommandRunner local;

    public WorkspacePrLookup(SshCommandRunner ssh) {
        this(ssh, new LocalCommandRunner());
    }

    public WorkspacePrLookup(SshCommandRunner ssh, LocalCommandRunner local) {
        this.ssh = ssh;
        this.local = local;
    }

    /// `cd <workspace> && gh pr view --json url` — `gh` has no `-C`, so the
    /// working directory is what picks the repository and branch. The path is
    /// single-quoted (spaces, no double quotes — see `SshCommandRunner`), and
    /// the JSON is parsed in Java rather than with `-q` for the same reason.
    static List<String> remoteCommand(String workspace) {
        return List.of("cd", SshCommandRunner.quote(workspace),
                "&&", "gh", "pr", "view", "--json", "url");
    }

    /// Every workspace of one host in **one** ssh round-trip:
    /// `echo '<marker /ws>'; ( cd '/ws' && gh pr view --json url ); …` — the
    /// subshell keeps a failed `cd` from leaking into the next workspace, and
    /// `;` keeps a workspace without a PR (`gh` exits 1) from ending the batch.
    /// The `gh` calls run one after another on the remote, which is why the
    /// caller hands this lookup a long-timeout runner: each one is a GitHub
    /// API call. One connection per task instead was a burst of simultaneous
    /// `ssh.exe` processes every tick (six of them for the same workspace).
    static List<String> batchCommand(Collection<String> workspaces) {
        List<String> command = new ArrayList<>();
        for (String workspace : workspaces) {
            if (!command.isEmpty()) {
                command.add(";");
            }
            command.add("echo");
            command.add(SshCommandRunner.quote(MARKER_PREFIX + workspace + MARKER_SUFFIX));
            command.add(";");
            command.add("(");
            command.addAll(remoteCommand(workspace));
            command.add(")");
        }
        return command;
    }

    /// Splits the batch output at the markers: workspace → PR URL, absent for
    /// workspaces whose section holds none.
    static Map<String, String> parseBatch(String stdout) {
        Map<String, String> urls = new LinkedHashMap<>();
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

    private static void addSection(Map<String, String> urls, @Nullable String workspace,
            StringBuilder section) {
        if (workspace == null) {
            return;
        }
        String url = parsePrUrl(section.toString());
        if (url != null) {
            urls.put(workspace, url);
        }
    }

    /// Local `gh` needs no `cd`: the working directory is set on the process.
    static List<String> localCommand() {
        return List.of("gh", "pr", "view", "--json", "url");
    }

    /// The workspace as a directory on *this* machine, or null when the path
    /// is not one (the usual remote case, and unparseable paths on Windows).
    static @Nullable Path localWorkspace(String workspace) {
        try {
            Path path = Path.of(workspace);
            return Files.isDirectory(path) ? path : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    static @Nullable String parsePrUrl(String json) {
        Matcher matcher = URL.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    /// The PR of the workspace's branch, or null when there is none, the
    /// workspace is gone, or `gh` is missing/unauthenticated (logged, not
    /// thrown).
    public @Nullable String findPrUrl(String host, String workspace) {
        Path directory = localWorkspace(workspace);
        if (directory != null) {
            try {
                LocalCommandRunner.LocalResult result = local.run(localCommand(), directory);
                if (result.ok()) {
                    return parsePrUrl(result.stdout());
                }
                Logger.debug("No PR for local workspace {}: {}", workspace, result.stderr().strip());
                return null;
            } catch (RuntimeException e) {
                Logger.debug("Local gh unavailable for {}: {}", workspace, e.getMessage());
            }
        }
        SshCommandRunner.SshResult result = ssh.run(host, remoteCommand(workspace));
        if (!result.ok()) {
            Logger.debug("No PR for workspace {} on {}: {}", workspace, host, result.stderr().strip());
            return null;
        }
        return parsePrUrl(result.stdout());
    }

    /// The PR of every given workspace's branch, workspaces without one
    /// absent. Workspaces that exist on this machine are asked locally, one
    /// `gh` each ([#findPrUrl]); the rest go to `host` in one ssh call
    /// ([#batchCommand]). The batch's exit code is the last `gh`'s, so only a
    /// failure that left no output at all (the connection itself) discards it.
    public Map<String, String> findPrUrls(String host, Collection<String> workspaces) {
        Map<String, String> urls = new LinkedHashMap<>();
        List<String> remote = new ArrayList<>();
        for (String workspace : workspaces) {
            if (localWorkspace(workspace) != null) {
                String url = findPrUrl(host, workspace);
                if (url != null) {
                    urls.put(workspace, url);
                }
            } else {
                remote.add(workspace);
            }
        }
        if (remote.isEmpty()) {
            return urls;
        }
        SshCommandRunner.SshResult result = ssh.run(host, batchCommand(remote));
        if (!result.ok() && result.stdout().isBlank()) {
            Logger.debug("No PR lookup for {} workspaces on {}: {}",
                    remote.size(), host, result.stderr().strip());
            return urls;
        }
        urls.putAll(parseBatch(result.stdout()));
        return urls;
    }
}
