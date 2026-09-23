package com.contextswitcher.switching;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.contextswitcher.local.LocalCommandRunner;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// The pull request's title for a GitHub PR URL, via the locally installed
/// (and authenticated) `gh` CLI — the system-tool approach of MADR 0003.
/// Falls back to `PR #<n> (<owner>/<repo>)` when `gh` is missing or fails.
/// A GitLab MR URL is answered by [GitLabMrLookup] instead (`glab`).
// [impl->dsn~task-from-pr~6]
// [impl->dsn~gitlab-mr-state~1]
public class PrTitleLookup {

    /// GitHub PR URL, capturing owner, repo, and number. A trailing slash is
    /// part of the same address, so a pasted `…/pull/123/` is a PR URL too
    /// (`dsn~browser-url-dedupe~3`).
    public static final Pattern PR_URL =
            Pattern.compile("https://github\\.com/([\\w.-]+)/([\\w.-]+)/pull/(\\d+)/?");

    private final LocalCommandRunner runner;

    public PrTitleLookup(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// Whether `url` is a pull request this app can look up: a GitHub PR or a
    /// GitLab merge request.
    public static boolean isPrOrMrUrl(String url) {
        return PR_URL.matcher(url).matches() || GitLabMrLookup.MR_URL.matcher(url).matches();
    }

    static List<String> command(String url) {
        return List.of("gh", "pr", "view", url, "--json", "title", "-q", ".title");
    }

    static @Nullable String fallbackTitle(String url) {
        Matcher matcher = PR_URL.matcher(url);
        return matcher.matches()
                ? "PR #%s (%s/%s)".formatted(matcher.group(3), matcher.group(1), matcher.group(2))
                : null;
    }

    /// Never null for a valid PR or MR URL; null only when `url` is neither.
    public @Nullable String title(String url) {
        if (GitLabMrLookup.MR_URL.matcher(url).matches()) {
            return new GitLabMrLookup(runner).title(url);
        }
        if (!PR_URL.matcher(url).matches()) {
            return null;
        }
        try {
            LocalCommandRunner.LocalResult result = runner.run(command(url));
            if (result.exitCode() == 0 && !result.stdout().isBlank()) {
                return result.stdout().strip();
            }
            Logger.debug("gh pr view failed for {} (exit {})", url, result.exitCode());
        } catch (RuntimeException e) {
            Logger.debug("gh unavailable for {}: {}", url, e.getMessage());
        }
        return fallbackTitle(url);
    }
}
