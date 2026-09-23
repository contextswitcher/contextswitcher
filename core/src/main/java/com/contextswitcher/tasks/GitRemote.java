package com.contextswitcher.tasks;

import org.jspecify.annotations.Nullable;

/// Turns a git remote URL into the repository's web URL — the fallback for a
/// category whose `CONTEXTSWITCHER.md` carries no `repo:` yet
/// (`dsn~terminal-issue-links~2`).
///
/// Handles the three forms `git config --get remote.origin.url` yields: the
/// scp-like `git@github.com:owner/repo.git`, `ssh://git@github.com/owner/repo`
/// and a plain `https://…` clone URL. User info, a port and the `.git` suffix
/// are dropped; anything else (a local path, an empty value) yields null.
/// Host-agnostic on purpose — a GitLab or Gitea remote maps just as well.
public final class GitRemote {

    private GitRemote() {
    }

    /// The `https://host/owner/repo` form of `remote`, or null when it is no
    /// recognizable remote URL.
    // [impl->dsn~terminal-issue-links~2]
    public static @Nullable String repoUrl(String remote) {
        String url = remote.strip();
        String hostAndPath;
        if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("ssh://")) {
            hostAndPath = url.substring(url.indexOf("//") + 2);
        } else if (url.indexOf('@') > 0 && url.indexOf(':') > url.indexOf('@')) {
            // scp-like: user@host:path — the colon is a separator, not a port.
            hostAndPath = url.replaceFirst(":", "/");
        } else {
            return null;
        }
        int at = hostAndPath.indexOf('@');
        int slash = hostAndPath.indexOf('/');
        if (at >= 0 && (slash < 0 || at < slash)) {
            hostAndPath = hostAndPath.substring(at + 1);
            slash = hostAndPath.indexOf('/');
        }
        if (slash <= 0) {
            return null;
        }
        // A port (ssh://git@host:22/owner/repo) is no part of the web URL.
        hostAndPath = hostAndPath.substring(0, slash).replaceFirst(":\\d+$", "")
                + hostAndPath.substring(slash);
        while (hostAndPath.endsWith("/")) {
            hostAndPath = hostAndPath.substring(0, hostAndPath.length() - 1);
        }
        if (hostAndPath.endsWith(".git")) {
            hostAndPath = hostAndPath.substring(0, hostAndPath.length() - 4);
        }
        return hostAndPath.indexOf('/') > 0 && !hostAndPath.endsWith("/")
                ? "https://" + hostAndPath
                : null;
    }
}
