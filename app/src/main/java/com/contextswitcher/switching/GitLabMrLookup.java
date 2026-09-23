package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.contextswitcher.local.LocalCommandRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// The GitLab half of [PrStateLookup]: the states and titles of merge
/// requests via the locally installed (and authenticated) `glab` CLI, the
/// same system-tool approach as `gh` (MADR 0003).
/// All MRs of one host go into **one** `glab api graphql` call (one alias per
/// MR), like the GitHub batch; path and iid travel as GraphQL variables, so
/// the argv carries no double quote.
/// Any host: the `/-/merge_requests/` path is GitLab's own, so gitlab.com and
/// a self-hosted instance are recognized alike — `glab` must be logged in to
/// that host.
// [impl->dsn~gitlab-mr-state~1]
public class GitLabMrLookup {

    /// GitLab MR URL, capturing host, project path (nested groups included)
    /// and iid. A trailing slash is the same address (`dsn~browser-url-dedupe~3`).
    public static final Pattern MR_URL = Pattern.compile(
            "https://([^/\\s]+)/((?:[\\w.-]+/)+[\\w.-]+)/-/merge_requests/(\\d+)/?");

    private final LocalCommandRunner runner;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitLabMrLookup(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// One aliased query for all `urls` (must all match [#MR_URL] and share
    /// `host`, in list order — alias `m<i>` is `urls[i]`).
    static List<String> command(String host, List<String> urls) {
        StringBuilder vars = new StringBuilder();
        StringBuilder body = new StringBuilder();
        List<String> argv = new ArrayList<>(List.of("glab", "api", "graphql", "--hostname", host));
        for (int i = 0; i < urls.size(); i++) {
            Matcher mr = MR_URL.matcher(urls.get(i));
            if (!mr.matches()) {
                throw new IllegalArgumentException("not an MR URL: " + urls.get(i));
            }
            vars.append(i == 0 ? "" : ", ").append("$p%1$d: ID!, $i%1$d: String!".formatted(i));
            body.append(" m%1$d: project(fullPath: $p%1$d) { mergeRequest(iid: $i%1$d) { state draft title labels(first: 30) { nodes { title color } } } }"
                    .formatted(i));
            argv.add("-f");
            argv.add("p" + i + "=" + mr.group(2));
            argv.add("-f");
            argv.add("i" + i + "=" + mr.group(3));
        }
        argv.add("-f");
        argv.add("query=query(" + vars + ") {" + body + " }");
        return argv;
    }

    /// `url -> info` parsed from the batch response; an alias without a
    /// merge request (deleted, no access) contributes nothing.
    Map<String, PrInfo> parse(String json, List<String> urls) {
        Map<String, PrInfo> states = new LinkedHashMap<>();
        try {
            JsonNode data = mapper.readTree(json).path("data");
            for (int i = 0; i < urls.size(); i++) {
                JsonNode mr = data.path("m" + i).path("mergeRequest");
                PrState state = PrState.from(mr.path("state").asText(), mr.path("draft").asBoolean());
                if (state != null) {
                    Map<String, String> labels = new LinkedHashMap<>();
                    // GitLab's color already carries the `#`.
                    mr.path("labels").path("nodes").forEach(node -> labels.put(
                            node.path("title").asText(), node.path("color").asText()));
                    states.put(urls.get(i), new PrInfo(state, mr.path("title").asText(), labels));
                }
            }
        } catch (java.io.IOException e) {
            Logger.debug("unparseable graphql MR-state response: {}", e.getMessage());
        }
        return states;
    }

    /// The states of all MR URLs in `urls` (others are skipped), one `glab`
    /// call per host. Failures are logged, not thrown; their MRs are missing.
    public Map<String, PrInfo> states(Collection<String> urls) {
        Map<String, List<String>> byHost = new TreeMap<>();
        urls.stream().distinct().sorted().forEach(url -> {
            Matcher mr = MR_URL.matcher(url);
            if (mr.matches()) {
                byHost.computeIfAbsent(mr.group(1), host -> new ArrayList<>()).add(url);
            }
        });
        Map<String, PrInfo> states = new LinkedHashMap<>();
        byHost.forEach((host, hostUrls) -> {
            try {
                LocalCommandRunner.LocalResult result = runner.run(command(host, hostUrls));
                if (!result.stdout().isBlank()) {
                    states.putAll(parse(result.stdout(), hostUrls));
                } else {
                    Logger.debug("glab api graphql MR states failed for {} (exit {})",
                            host, result.exitCode());
                }
            } catch (RuntimeException e) {
                Logger.debug("glab unavailable for MR states: {}", e.getMessage());
            }
        });
        return states;
    }

    /// The MR's title, `glab` missing or failing falls back to
    /// `MR !<iid> (<path>)`; null only when `url` is no MR URL.
    public @Nullable String title(String url) {
        Matcher mr = MR_URL.matcher(url);
        if (!mr.matches()) {
            return null;
        }
        PrInfo info = states(List.of(url)).get(url);
        return info != null && !info.title().isBlank()
                ? info.title()
                : "MR !%s (%s)".formatted(mr.group(3), mr.group(2));
    }
}
