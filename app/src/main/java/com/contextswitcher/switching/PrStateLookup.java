package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;

import com.contextswitcher.local.LocalCommandRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tinylog.Logger;

/// The states of GitHub PRs via the locally installed (and authenticated)
/// `gh` CLI — the system-tool approach of MADR 0003, like [PrTitleLookup].
/// All URLs go into **one** `gh api graphql` call (one alias per PR), so a
/// poll round costs a single GitHub API request instead of one per PR —
/// per-PR `gh pr view` rounds tripped GitHub's secondary rate limit.
/// Owner/repo/number travel as GraphQL variables, so the argv carries no
/// double quote (the Windows trap); owner and repo use `-f` (raw string),
/// since `-F` would turn an all-digit owner login into an Int.
// [impl->dsn~pr-state-indicator~3]
// [impl->dsn~pr-poll-economy~2]
public class PrStateLookup {

    private final LocalCommandRunner runner;
    private final GitLabMrLookup gitLab;
    private final ObjectMapper mapper = new ObjectMapper();

    public PrStateLookup(LocalCommandRunner runner) {
        this.runner = runner;
        this.gitLab = new GitLabMrLookup(runner);
    }

    /// One aliased query for all `urls` (must all match [PrTitleLookup#PR_URL],
    /// in list order — alias `p<i>` is `urls[i]`).
    static List<String> command(List<String> urls) {
        StringBuilder vars = new StringBuilder();
        StringBuilder body = new StringBuilder();
        List<String> argv = new ArrayList<>(List.of("gh", "api", "graphql"));
        for (int i = 0; i < urls.size(); i++) {
            Matcher pr = PrTitleLookup.PR_URL.matcher(urls.get(i));
            if (!pr.matches()) {
                throw new IllegalArgumentException("not a PR URL: " + urls.get(i));
            }
            vars.append(i == 0 ? "" : ", ")
                    .append("$o%1$d: String!, $r%1$d: String!, $n%1$d: Int!".formatted(i));
            body.append(" p%1$d: repository(owner: $o%1$d, name: $r%1$d)"
                    .formatted(i))
                    .append(" { pullRequest(number: $n%1$d) { state isDraft isInMergeQueue reviewDecision title labels(first: 30) { nodes { name color } } } }".formatted(i));
            argv.add("-f");
            argv.add("o" + i + "=" + pr.group(1));
            argv.add("-f");
            argv.add("r" + i + "=" + pr.group(2));
            argv.add("-F");
            argv.add("n" + i + "=" + pr.group(3));
        }
        argv.add("-f");
        argv.add("query=query(" + vars + ") {" + body + " }");
        return argv;
    }

    /// `url -> info` parsed from the batch response; an alias missing from
    /// `data` (deleted repo, no access) contributes nothing, the rest of the
    /// batch survives.
    // [impl->dsn~pr-header-line~4]
    // [impl->dsn~pr-status-grouping~1]
    Map<String, PrInfo> parse(String json, List<String> urls) {
        Map<String, PrInfo> states = new LinkedHashMap<>();
        try {
            JsonNode data = mapper.readTree(json).path("data");
            for (int i = 0; i < urls.size(); i++) {
                JsonNode pr = data.path("p" + i).path("pullRequest");
                PrState state = PrState.from(pr.path("state").asText(),
                        pr.path("isDraft").asBoolean());
                if (state != null) {
                    Map<String, String> labels = new LinkedHashMap<>();
                    pr.path("labels").path("nodes").forEach(node -> labels.put(
                            node.path("name").asText(), "#" + node.path("color").asText()));
                    states.put(urls.get(i), new PrInfo(state, pr.path("title").asText(), labels,
                            pr.path("isInMergeQueue").asBoolean(),
                            "CHANGES_REQUESTED".equals(pr.path("reviewDecision").asText())));
                }
            }
        } catch (java.io.IOException e) {
            Logger.debug("unparseable graphql PR-state response: {}", e.getMessage());
        }
        return states;
    }

    /// The states of all PR URLs in `urls` (non-PR URLs are skipped), from one
    /// `gh` call — GitLab MR URLs from [GitLabMrLookup] alongside
    /// (`dsn~gitlab-mr-state~1`). Empty when the tools are missing/fail or
    /// nothing was resolvable (all failures logged, not thrown) — callers
    /// keep their last known states.
    // [impl->dsn~gitlab-mr-state~1]
    public Map<String, PrInfo> states(Collection<String> urls) {
        Map<String, PrInfo> mrStates = gitLab.states(urls);
        Map<String, PrInfo> prStates = gitHubStates(urls);
        if (mrStates.isEmpty()) {
            return prStates;
        }
        Map<String, PrInfo> all = new LinkedHashMap<>(prStates);
        all.putAll(mrStates);
        return all;
    }

    private Map<String, PrInfo> gitHubStates(Collection<String> urls) {
        List<String> prUrls = urls.stream().distinct()
                .filter(url -> PrTitleLookup.PR_URL.matcher(url).matches())
                .sorted().toList();
        if (prUrls.isEmpty()) {
            return Map.of();
        }
        try {
            LocalCommandRunner.LocalResult result = runner.run(command(prUrls));
            if (!result.stdout().isBlank()) {
                // exit != 0 still carries data for the aliases that resolved
                // (gh reports per-alias errors alongside partial data).
                return parse(result.stdout(), prUrls);
            }
            Logger.debug("gh api graphql PR states failed (exit {})", result.exitCode());
        } catch (RuntimeException e) {
            Logger.debug("gh unavailable for PR states: {}", e.getMessage());
        }
        return Map.of();
    }
}
