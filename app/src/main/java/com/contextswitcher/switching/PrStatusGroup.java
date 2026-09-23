package com.contextswitcher.switching;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/// The bucket a PR files under when the task list groups by PR status.
/// Knobless: a repository whose PRs carry `status: …` labels (JabRef's review
/// workflow) is grouped by those labels; any other repository by GitHub's own
/// states. Merge queue, merged, and closed win over both, since a label is
/// often left behind once a PR moves on.
// [impl->dsn~pr-status-grouping~1]
public final class PrStatusGroup {

    static final String STATUS_PREFIX = "status:";
    static final String NO_STATUS = "no status";

    /// Workflow order of the buckets; anything else (an unknown `status:`
    /// label) sorts with [#NO_STATUS], alphabetically.
    private static final List<String> ORDER = List.of(
            "draft", "changes requested", "status: changes-required",
            "ready", "status: ready-for-review", "status: awaits-second-review",
            NO_STATUS, "merge queue", "merged", "closed");

    /// Buckets in workflow order, then alphabetical.
    public static final Comparator<String> BY_WORKFLOW = Comparator
            .comparingInt(PrStatusGroup::rank)
            .thenComparing(String.CASE_INSENSITIVE_ORDER);

    private PrStatusGroup() {
    }

    /// The `owner/repo` keys of every repository in which at least one known
    /// PR carries a `status:` label.
    public static Set<String> labelRepos(Map<String, PrInfo> known) {
        return known.entrySet().stream()
                .filter(entry -> statusLabel(entry.getValue()) != null)
                .map(entry -> repo(entry.getKey()))
                .filter(repo -> !repo.isEmpty())
                .collect(Collectors.toSet());
    }

    /// The bucket for the PR at `url`.
    public static String bucket(String url, PrInfo info, Set<String> labelRepos) {
        return switch (info.state()) {
            case MERGED -> "merged";
            case CLOSED -> "closed";
            case OPEN, DRAFT -> {
                if (info.inMergeQueue()) {
                    yield "merge queue";
                }
                if (labelRepos.contains(repo(url))) {
                    String label = statusLabel(info);
                    yield label != null ? label : info.state() == PrState.DRAFT ? "draft" : NO_STATUS;
                }
                yield info.state() == PrState.DRAFT ? "draft"
                        : info.changesRequested() ? "changes requested" : "ready";
            }
        };
    }

    private static @Nullable String statusLabel(PrInfo info) {
        return info.labels().keySet().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(STATUS_PREFIX))
                .findFirst().orElse(null);
    }

    private static String repo(String url) {
        Matcher pr = PrTitleLookup.PR_URL.matcher(url);
        return pr.matches() ? (pr.group(1) + "/" + pr.group(2)).toLowerCase(Locale.ROOT) : "";
    }

    private static int rank(String bucket) {
        int index = ORDER.indexOf(bucket.toLowerCase(Locale.ROOT));
        return index >= 0 ? index : ORDER.indexOf(NO_STATUS);
    }
}
