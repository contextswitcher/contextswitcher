package com.contextswitcher.terminal;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.techsenger.jeditermfx.core.model.hyperlinks.HyperlinkFilter;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkInfo;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Makes `#123`, `PR 123` and the qualified `JabRef#123` / `JabRef/jabref#123`
/// in the mirrored terminal clickable, next to the plain URLs of
/// `DefaultHyperlinkFilter`: Claude writes issue and PR numbers in those forms
/// all day, and they are the one link in the pane that has no URL to click.
///
/// An unqualified number belongs to the repository of the mirrored task's
/// category (supplied fresh on every click, since selecting another task
/// changes it) and opens as `<repo>/issues/<n>` — GitHub redirects that to the
/// pull request when the number is one, so issues and PRs need no telling
/// apart. A qualified one names its own repository, which is how Claude refers
/// to a *foreign* repository while working in another: `JabRef/jabref#17103`
/// spells out owner and name, the short `JabRef#17103` is resolved against the
/// repositories the app knows from its categories. Neither is a link when
/// nothing resolves it.
// [impl->dsn~terminal-issue-links~2]
public class IssueHyperlinkFilter implements HyperlinkFilter {

    /// An optional `owner/name` or `name` prefix, then `#` + digits, not
    /// inside a longer token, or a standalone `PR` word followed by digits:
    /// `#17038`, `JabRef#17103` and `PR 17038` link, the `#1a2b3c` of a color
    /// does not — and `pane#2` only if a repository is actually called `pane`.
    private static final Pattern ISSUE = Pattern.compile(
            "(?<![\\w#/-])(?:([A-Za-z0-9][\\w.-]*)/)?([A-Za-z0-9][\\w.-]*)?#(\\d+)\\b"
                    + "|\\bPR\\s+(\\d+)\\b",
            Pattern.CASE_INSENSITIVE);

    private final Supplier<@Nullable String> repoUrl;
    private final Supplier<Collection<String>> knownRepos;

    public IssueHyperlinkFilter(Supplier<@Nullable String> repoUrl) {
        this(repoUrl, Set::of);
    }

    /// `knownRepos` are the repository URLs of the app's categories — what a
    /// short `JabRef#123` is matched against by owner or name.
    public IssueHyperlinkFilter(Supplier<@Nullable String> repoUrl,
            Supplier<Collection<String>> knownRepos) {
        this.repoUrl = repoUrl;
        this.knownRepos = knownRepos;
    }

    @Override
    public @Nullable LinkResult apply(String line) {
        List<LinkResultItem> items = new ArrayList<>();
        Matcher matcher = ISSUE.matcher(line);
        while (matcher.find()) {
            String url = urlOf(matcher);
            if (url != null) {
                items.add(new LinkResultItem(matcher.start(), matcher.end(),
                        new LinkInfo(() -> open(url))));
            }
        }
        return items.isEmpty() ? null : new LinkResult(items);
    }

    /// The URL a matched token opens — for the status-bar hover text of
    /// `dsn~terminal-link-hover~1`. Null when the text carries no number, or
    /// when no repository resolves it.
    public @Nullable String urlFor(String linkText) {
        Matcher matcher = ISSUE.matcher(linkText);
        return matcher.find() ? urlOf(matcher) : null;
    }

    private @Nullable String urlOf(Matcher matcher) {
        if (matcher.group(4) != null) {
            return issueUrl(repoUrl.get(), matcher.group(4));
        }
        String owner = matcher.group(1);
        String name = matcher.group(2);
        String number = matcher.group(3);
        if (name == null) {
            return issueUrl(repoUrl.get(), number);
        }
        if (owner != null) {
            return issueUrl(baseUrl() + "/" + owner + "/" + name, number);
        }
        return issueUrl(repoNamed(name), number);
    }

    /// The repository a short prefix stands for: the mirrored task's own when
    /// its owner or name matches, else the first category repository that
    /// does. Null when the prefix names nothing the app knows — a `pane#2`
    /// label must not become a link.
    private @Nullable String repoNamed(String prefix) {
        String current = repoUrl.get();
        if (current != null && matches(current, prefix)) {
            return current;
        }
        for (String known : knownRepos.get()) {
            if (matches(known, prefix)) {
                return known;
            }
        }
        return null;
    }

    /// True when `prefix` is the owner or the name of `repo` — GitHub is
    /// case-insensitive there, and so is the way Claude writes them.
    private static boolean matches(String repo, String prefix) {
        String[] segments = repo.replaceAll("/+$", "").split("/");
        return segments.length >= 2
                && (segments[segments.length - 1].equalsIgnoreCase(prefix)
                        || segments[segments.length - 2].equalsIgnoreCase(prefix));
    }

    /// Scheme and host of the mirrored task's repository — a qualified
    /// `owner/name#123` stays on the forge the session is working against
    /// (a self-hosted GitLab as much as github.com), which is the only forge
    /// the pane has any evidence of. Falls back to github.com.
    private String baseUrl() {
        String repo = repoUrl.get();
        if (repo != null) {
            try {
                URI uri = new URI(repo);
                if (uri.getScheme() != null && uri.getHost() != null) {
                    return uri.getScheme() + "://" + uri.getHost();
                }
            } catch (URISyntaxException e) {
                Logger.debug("Not a URL, using github.com: {}", repo);
            }
        }
        return "https://github.com";
    }

    private static @Nullable String issueUrl(@Nullable String repo, String number) {
        if (repo == null) {
            return null;
        }
        return repo.endsWith("/") ? repo + "issues/" + number : repo + "/issues/" + number;
    }

    /// The system browser, like the URL links of `dsn~terminal-hyperlinks~1`.
    private void open(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) {
            Logger.warn("Cannot open {} ({})", url, e.getMessage());
        }
    }
}
